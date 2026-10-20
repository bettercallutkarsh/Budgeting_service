package com.budgetingapp.service;

import com.budgetingapp.dto.request.CreditCardOpeningRequest;
import com.budgetingapp.dto.request.MoneyEntryCreateRequest;
import com.budgetingapp.dto.request.MoneyEntryListQuery;
import com.budgetingapp.dto.request.MoneyEntryVoidRequest;
import com.budgetingapp.dto.response.CreditCardSummaryResponse;
import com.budgetingapp.dto.response.MoneyEntryPageResponse;
import com.budgetingapp.dto.response.MoneyEntryResponse;
import com.budgetingapp.entity.MoneyEntry;
import com.budgetingapp.entity.MonthlyBudget;
import com.budgetingapp.entity.User;
import com.budgetingapp.entity.enums.BudgetBucket;
import com.budgetingapp.entity.enums.EntryStatus;
import com.budgetingapp.entity.enums.EntryType;
import com.budgetingapp.entity.enums.PaymentMethod;
import com.budgetingapp.exception.BusinessException;
import com.budgetingapp.exception.PeriodReadOnlyException;
import com.budgetingapp.repository.MoneyEntryRepository;
import com.budgetingapp.repository.MonthlyBudgetRepository;
import com.budgetingapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class MoneyEntryServiceTest {

    private static final Long USER_ID = 1L;
    private static final String UUID_1 = "11111111-2222-3333-4444-555555555555";
    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");
    private static final Instant ENTRY_INSTANT = Instant.parse("2026-10-18T06:30:00Z");
    private static final Instant CARD_START = Instant.parse("2026-10-01T00:00:00Z");

    @Mock
    private MoneyEntryRepository moneyEntryRepository;

    @Mock
    private MonthlyBudgetRepository budgetRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private MoneyEntryService service;
    private final AtomicReference<MoneyEntry> savedRef = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Kolkata"));
        service = new MoneyEntryService(moneyEntryRepository, budgetRepository, userRepository,
                transactionManager, clock);
        savedRef.set(null);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void createsExpenseEntryWithEssentialsBucketAndIstEntryTime() {
        stubLockUser(user(USER_ID));
        stubSaveAndRefetch(100L);

        MoneyEntryResponse response = service.createEntry(USER_ID, expenseRequest());

        assertEquals(Long.valueOf(100L), response.getMoneyEntryId());
        assertEquals(ENTRY_INSTANT, response.getEntryAtUtc());
        assertEquals(EntryType.EXPENSE, response.getEntryType());
        assertEquals(BudgetBucket.ESSENTIALS, response.getBucket());
        assertEquals("GROCERIES", response.getCategoryCode());
        assertEquals(0, new BigDecimal("250.50").compareTo(response.getAmount()));
        assertEquals(2, response.getAmount().scale());
        assertEquals(PaymentMethod.UPI, response.getPaymentMethod());
        assertEquals(EntryStatus.POSTED, response.getStatus());
        assertNull(response.getClientRequestId());
        assertNull(response.getNote());
        assertNull(response.getVoidedAtUtc());

        ArgumentCaptor<MoneyEntry> captor = ArgumentCaptor.forClass(MoneyEntry.class);
        verify(moneyEntryRepository).saveAndFlush(captor.capture());
        assertEquals(USER_ID, captor.getValue().getUser().getUserId());
        assertEquals(EntryStatus.POSTED, captor.getValue().getStatus());
        verify(userRepository).findWithLockByUserId(USER_ID);
    }

    @Test
    void createsSafetySavingEntryWithoutPaymentMethod() {
        stubLockUser(user(USER_ID));
        stubSaveAndRefetch(101L);
        MoneyEntryCreateRequest request = expenseRequest();
        request.setEntryType(EntryType.SAFETY_SAVING);
        request.setBucket(BudgetBucket.SAFETY);
        request.setCategoryCode("EMERGENCY_FUND");
        request.setPaymentMethod(null);
        request.setAmount(new BigDecimal("1000.00"));

        MoneyEntryResponse response = service.createEntry(USER_ID, request);

        assertEquals(EntryType.SAFETY_SAVING, response.getEntryType());
        assertEquals(BudgetBucket.SAFETY, response.getBucket());
        assertEquals("EMERGENCY_FUND", response.getCategoryCode());
        assertNull(response.getPaymentMethod());
    }

    @Test
    void createsInvestmentEntryWhenBudgetEnabled() {
        stubLockUser(user(USER_ID));
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.of(budget(true)));
        stubSaveAndRefetch(102L);

        MoneyEntryResponse response = service.createEntry(USER_ID, investmentRequest());

        assertEquals(EntryType.INVESTMENT, response.getEntryType());
        assertEquals(BudgetBucket.INVESTMENTS, response.getBucket());
        assertEquals("SIP", response.getCategoryCode());
        assertEquals(0, new BigDecimal("5000.00").compareTo(response.getAmount()));
    }

    @Test
    void createsCreditCardPaymentWithinOutstanding() {
        User cardUser = userWithCard(CARD_START, "5000.00");
        stubLockUser(cardUser);
        when(moneyEntryRepository
                .sumAmountByUser_UserIdAndEntryTypeAndPaymentMethodAndStatusAndEntryAtUtcGreaterThanEqual(
                        USER_ID, EntryType.EXPENSE, PaymentMethod.CREDIT_CARD, EntryStatus.POSTED, CARD_START))
                .thenReturn(new BigDecimal("1200.00"));
        when(moneyEntryRepository.sumAmountByUser_UserIdAndEntryTypeAndStatusAndEntryAtUtcGreaterThanEqual(
                USER_ID, EntryType.CREDIT_CARD_PAYMENT, EntryStatus.POSTED, CARD_START))
                .thenReturn(new BigDecimal("200.00"));
        stubSaveAndRefetch(103L);

        MoneyEntryResponse response = service.createEntry(USER_ID, creditCardPaymentRequest("300.00"));

        assertEquals(EntryType.CREDIT_CARD_PAYMENT, response.getEntryType());
        assertNull(response.getBucket());
        assertEquals("CREDIT_CARD_BILL_PAYMENT", response.getCategoryCode());
        assertEquals(0, new BigDecimal("300.00").compareTo(response.getAmount()));
    }

    @Test
    void createsCreditCardPurchaseWhenTrackingConfigured() {
        stubLockUser(userWithCard(CARD_START, "5000.00"));
        stubSaveAndRefetch(104L);
        MoneyEntryCreateRequest request = expenseRequest();
        request.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        request.setCategoryCode("SHOPPING");
        request.setAmount(new BigDecimal("800.00"));

        MoneyEntryResponse response = service.createEntry(USER_ID, request);

        assertEquals(PaymentMethod.CREDIT_CARD, response.getPaymentMethod());
        assertEquals("SHOPPING", response.getCategoryCode());
    }

    @Test
    void storesBlankNoteAndBlankClientRequestIdAsNull() {
        stubLockUser(user(USER_ID));
        stubSaveAndRefetch(105L);
        MoneyEntryCreateRequest request = expenseRequest();
        request.setNote(" ");
        request.setClientRequestId(" ");

        service.createEntry(USER_ID, request);

        ArgumentCaptor<MoneyEntry> captor = ArgumentCaptor.forClass(MoneyEntry.class);
        verify(moneyEntryRepository).saveAndFlush(captor.capture());
        assertNull(captor.getValue().getNote());
        assertNull(captor.getValue().getClientRequestId());
    }

    @Test
    void returnsExistingEntryOnIdempotentReplay() {
        stubLockUser(user(USER_ID));
        when(moneyEntryRepository.findByUser_UserIdAndClientRequestId(USER_ID, UUID_1))
                .thenReturn(Optional.of(matchingExistingEntry(55L, "250.50")));
        MoneyEntryCreateRequest request = expenseRequest();
        request.setClientRequestId(UUID_1);

        MoneyEntryResponse response = service.createEntry(USER_ID, request);

        assertEquals(Long.valueOf(55L), response.getMoneyEntryId());
        assertEquals(0, new BigDecimal("250.50").compareTo(response.getAmount()));
        verify(moneyEntryRepository, never()).saveAndFlush(any(MoneyEntry.class));
    }

    @Test
    void recoversExistingEntryWhenUniqueConstraintViolated() {
        stubLockUser(user(USER_ID));
        when(moneyEntryRepository.findByUser_UserIdAndClientRequestId(USER_ID, UUID_1))
                .thenReturn(Optional.empty(), Optional.of(matchingExistingEntry(55L, "250.50")));
        when(moneyEntryRepository.saveAndFlush(any(MoneyEntry.class)))
                .thenThrow(new DataIntegrityViolationException("uq_money_entries_user_request"));
        MoneyEntryCreateRequest request = expenseRequest();
        request.setClientRequestId(UUID_1);

        MoneyEntryResponse response = service.createEntry(USER_ID, request);

        assertEquals(Long.valueOf(55L), response.getMoneyEntryId());
        verify(moneyEntryRepository, times(1)).saveAndFlush(any(MoneyEntry.class));
    }

    @Test
    void throwsConflictWhenReplayedPayloadDiffers() {
        stubLockUser(user(USER_ID));
        when(moneyEntryRepository.findByUser_UserIdAndClientRequestId(USER_ID, UUID_1))
                .thenReturn(Optional.empty(), Optional.of(matchingExistingEntry(55L, "999.99")));
        when(moneyEntryRepository.saveAndFlush(any(MoneyEntry.class)))
                .thenThrow(new DataIntegrityViolationException("uq_money_entries_user_request"));
        MoneyEntryCreateRequest request = expenseRequest();
        request.setClientRequestId(UUID_1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, request));

        assertFailure(exception, HttpStatus.CONFLICT, "CLIENT_REQUEST_ID_CONFLICT");
    }

    @Test
    void throwsEntrySaveFailedWithoutRequestIdOnIntegrityViolation() {
        stubLockUser(user(USER_ID));
        when(moneyEntryRepository.saveAndFlush(any(MoneyEntry.class)))
                .thenThrow(new DataIntegrityViolationException("chk_money_entries_payment_required"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, expenseRequest()));

        assertFailure(exception, HttpStatus.INTERNAL_SERVER_ERROR, "ENTRY_SAVE_FAILED");
    }

    @Test
    void throwsEntrySaveFailedWhenRecoveryFindsNothing() {
        stubLockUser(user(USER_ID));
        when(moneyEntryRepository.findByUser_UserIdAndClientRequestId(USER_ID, UUID_1))
                .thenReturn(Optional.empty(), Optional.empty());
        when(moneyEntryRepository.saveAndFlush(any(MoneyEntry.class)))
                .thenThrow(new DataIntegrityViolationException("uq_money_entries_user_request"));
        MoneyEntryCreateRequest request = expenseRequest();
        request.setClientRequestId(UUID_1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, request));

        assertFailure(exception, HttpStatus.INTERNAL_SERVER_ERROR, "ENTRY_SAVE_FAILED");
    }

    @Test
    void rejectsNullCreateRequest() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, null));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "REQUEST_INVALID");
    }

    @Test
    void rejectsMissingAmount() {
        MoneyEntryCreateRequest request = expenseRequest();
        request.setAmount(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, request));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "REQUEST_INVALID");
    }

    @Test
    void rejectsExpenseWithInvestmentsBucket() {
        MoneyEntryCreateRequest request = expenseRequest();
        request.setBucket(BudgetBucket.INVESTMENTS);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, request));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "INVALID_BUCKET_FOR_ENTRY_TYPE");
    }

    @Test
    void rejectsCreditCardPaymentWithBucket() {
        MoneyEntryCreateRequest request = expenseRequest();
        request.setEntryType(EntryType.CREDIT_CARD_PAYMENT);
        request.setBucket(BudgetBucket.ESSENTIALS);
        request.setCategoryCode("CREDIT_CARD_BILL_PAYMENT");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, request));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "INVALID_BUCKET_FOR_ENTRY_TYPE");
    }

    @Test
    void rejectsSafetySavingWithoutSafetyBucket() {
        MoneyEntryCreateRequest request = expenseRequest();
        request.setEntryType(EntryType.SAFETY_SAVING);
        request.setBucket(null);
        request.setCategoryCode("EMERGENCY_FUND");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, request));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "INVALID_BUCKET_FOR_ENTRY_TYPE");
    }

    @Test
    void rejectsExpenseWithoutPaymentMethod() {
        MoneyEntryCreateRequest request = expenseRequest();
        request.setPaymentMethod(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, request));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "PAYMENT_METHOD_REQUIRED");
    }

    @Test
    void rejectsNonExpenseWithCreditCardPaymentMethod() {
        MoneyEntryCreateRequest request = expenseRequest();
        request.setEntryType(EntryType.SAFETY_SAVING);
        request.setBucket(BudgetBucket.SAFETY);
        request.setCategoryCode("EMERGENCY_FUND");
        request.setPaymentMethod(PaymentMethod.CREDIT_CARD);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, request));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_METHOD_FOR_ENTRY_TYPE");
    }

    @Test
    void rejectsUnknownCategoryCode() {
        MoneyEntryCreateRequest request = expenseRequest();
        request.setCategoryCode("TRAVEL");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, request));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "CATEGORY_CODE_UNKNOWN");
    }

    @Test
    void rejectsCategoryNotAllowedForBucket() {
        MoneyEntryCreateRequest request = expenseRequest();
        request.setBucket(BudgetBucket.SAFETY);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, request));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "INVALID_CATEGORY_FOR_BUCKET");
    }

    @Test
    void rejectsZeroAmount() {
        MoneyEntryCreateRequest request = expenseRequest();
        request.setAmount(BigDecimal.ZERO);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, request));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "AMOUNT_INVALID");
    }

    @Test
    void rejectsAmountAboveMaximum() {
        MoneyEntryCreateRequest request = expenseRequest();
        request.setAmount(new BigDecimal("1000000000000.00"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, request));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "AMOUNT_INVALID");
    }

    @Test
    void rejectsAmountWithTooManyDecimals() {
        MoneyEntryCreateRequest request = expenseRequest();
        request.setAmount(new BigDecimal("10.999"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, request));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "AMOUNT_PRECISION");
    }

    @Test
    void rejectsMalformedClientRequestId() {
        MoneyEntryCreateRequest request = expenseRequest();
        request.setClientRequestId("abc");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, request));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "CLIENT_REQUEST_ID_INVALID");
    }

    @Test
    void rejectsPastMonthEntry() {
        MoneyEntryCreateRequest request = expenseRequest();
        request.setEntryDate(LocalDate.of(2026, 9, 30));

        Exception exception = assertThrows(Exception.class,
                () -> service.createEntry(USER_ID, request));

        assertTrue(exception instanceof PeriodReadOnlyException);
        BusinessException business = (BusinessException) exception;
        assertFailure(business, HttpStatus.CONFLICT, "PERIOD_READ_ONLY");
        assertTrue(business.getMessage().contains("entry period"));
    }

    @Test
    void rejectsFutureDayEntry() {
        MoneyEntryCreateRequest request = expenseRequest();
        request.setEntryDate(LocalDate.of(2026, 10, 19));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, request));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "ENTRY_DATE_FUTURE");
    }

    @Test
    void rejectsLaterTimeToday() {
        MoneyEntryCreateRequest request = expenseRequest();
        request.setEntryTime(LocalTime.of(22, 0));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, request));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "ENTRY_DATE_FUTURE");
    }

    @Test
    void rejectsInvestmentWithoutBudget() {
        stubLockUser(user(USER_ID));
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, investmentRequest()));

        assertFailure(exception, HttpStatus.NOT_FOUND, "BUDGET_NOT_SET");
    }

    @Test
    void rejectsInvestmentWhenDisabled() {
        stubLockUser(user(USER_ID));
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.of(budget(false)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, investmentRequest()));

        assertFailure(exception, HttpStatus.CONFLICT, "INVESTMENTS_DISABLED");
    }

    @Test
    void rejectsCreditCardPurchaseWithoutTracking() {
        stubLockUser(user(USER_ID));
        MoneyEntryCreateRequest request = expenseRequest();
        request.setPaymentMethod(PaymentMethod.CREDIT_CARD);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, request));

        assertFailure(exception, HttpStatus.CONFLICT, "CREDIT_CARD_TRACKING_NOT_CONFIGURED");
    }

    @Test
    void rejectsCreditCardPaymentWithoutTracking() {
        stubLockUser(user(USER_ID));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, creditCardPaymentRequest("300.00")));

        assertFailure(exception, HttpStatus.CONFLICT, "CREDIT_CARD_TRACKING_NOT_CONFIGURED");
    }

    @Test
    void rejectsCreditCardPaymentBeforeTrackingStart() {
        stubLockUser(userWithCard(Instant.parse("2026-10-18T08:00:00Z"), "5000.00"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, creditCardPaymentRequest("300.00")));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "PAYMENT_BEFORE_TRACKING_START");
    }

    @Test
    void rejectsCreditCardPaymentExceedingOutstanding() {
        stubLockUser(userWithCard(CARD_START, "100.00"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, creditCardPaymentRequest("150.00")));

        assertFailure(exception, HttpStatus.CONFLICT, "PAYMENT_EXCEEDS_OUTSTANDING");
    }

    @Test
    void rejectsUnknownUserOnCreate() {
        when(userRepository.findWithLockByUserId(USER_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, expenseRequest()));

        assertFailure(exception, HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
    }

    @Test
    void rejectsOversizedNote() {
        MoneyEntryCreateRequest request = expenseRequest();
        request.setNote("n".repeat(501));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEntry(USER_ID, request));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "NOTE_TOO_LONG");
    }

    @Test
    void voidsPostedEntryWithReason() {
        stubLockUser(user(USER_ID));
        when(moneyEntryRepository.findByMoneyEntryIdAndUser_UserId(7L, USER_ID))
                .thenReturn(Optional.of(postedEntry(7L)));
        stubSaveAndRefetch(7L);
        MoneyEntryVoidRequest request = new MoneyEntryVoidRequest();
        request.setVoidReason("duplicate entry");

        MoneyEntryResponse response = service.voidEntry(USER_ID, 7L, request);

        assertEquals(Long.valueOf(7L), response.getMoneyEntryId());
        assertEquals(EntryStatus.VOID, response.getStatus());
        assertEquals(NOW, response.getVoidedAtUtc());
        assertEquals("duplicate entry", response.getVoidReason());
    }

    @Test
    void voidsEntryWithBlankReasonAsNull() {
        stubLockUser(user(USER_ID));
        when(moneyEntryRepository.findByMoneyEntryIdAndUser_UserId(7L, USER_ID))
                .thenReturn(Optional.of(postedEntry(7L)));
        stubSaveAndRefetch(7L);
        MoneyEntryVoidRequest request = new MoneyEntryVoidRequest();
        request.setVoidReason(" ");

        MoneyEntryResponse response = service.voidEntry(USER_ID, 7L, request);

        assertEquals(EntryStatus.VOID, response.getStatus());
        assertNull(response.getVoidReason());
    }

    @Test
    void rejectsVoidOfMissingEntry() {
        stubLockUser(user(USER_ID));
        when(moneyEntryRepository.findByMoneyEntryIdAndUser_UserId(7L, USER_ID))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.voidEntry(USER_ID, 7L, new MoneyEntryVoidRequest()));

        assertFailure(exception, HttpStatus.NOT_FOUND, "ENTRY_NOT_FOUND");
    }

    @Test
    void rejectsVoidOfAlreadyVoidEntry() {
        stubLockUser(user(USER_ID));
        MoneyEntry voided = postedEntry(7L);
        voided.setStatus(EntryStatus.VOID);
        when(moneyEntryRepository.findByMoneyEntryIdAndUser_UserId(7L, USER_ID))
                .thenReturn(Optional.of(voided));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.voidEntry(USER_ID, 7L, new MoneyEntryVoidRequest()));

        assertFailure(exception, HttpStatus.CONFLICT, "ALREADY_VOIDED");
        verify(moneyEntryRepository, never()).saveAndFlush(any(MoneyEntry.class));
    }

    @Test
    void rejectsNullVoidRequest() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.voidEntry(USER_ID, 7L, null));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "REQUEST_INVALID");
    }

    @Test
    void rejectsOversizedVoidReason() {
        MoneyEntryVoidRequest request = new MoneyEntryVoidRequest();
        request.setVoidReason("r".repeat(251));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.voidEntry(USER_ID, 7L, request));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "VOID_REASON_TOO_LONG");
    }

    @Test
    void listsEntriesWithDefaultsAndDescendingSort() {
        Pageable expectedPageable = PageRequest.of(0, 20,
                Sort.by(Sort.Order.desc("entryAtUtc"), Sort.Order.desc("moneyEntryId")));
        when(moneyEntryRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(postedEntry(9L)), expectedPageable, 1));

        MoneyEntryPageResponse response = service.listEntries(USER_ID, null);

        assertEquals(1, response.getContent().size());
        assertEquals(0, response.getPage());
        assertEquals(20, response.getSize());
        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getTotalPages());
        assertEquals(Long.valueOf(9L), response.getContent().get(0).getMoneyEntryId());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(moneyEntryRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("entryAtUtc").getDirection());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("moneyEntryId").getDirection());
    }

    @Test
    void clampsOversizedPageSize() {
        MoneyEntryListQuery query = new MoneyEntryListQuery();
        query.setSize(500);
        Pageable expectedPageable = PageRequest.of(0, 100,
                Sort.by(Sort.Order.desc("entryAtUtc"), Sort.Order.desc("moneyEntryId")));
        when(moneyEntryRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), expectedPageable, 0));

        service.listEntries(USER_ID, query);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(moneyEntryRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertEquals(100, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void rejectsIncompleteDateRange() {
        MoneyEntryListQuery query = new MoneyEntryListQuery();
        query.setFromDate(LocalDate.of(2026, 10, 1));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.listEntries(USER_ID, query));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "DATE_RANGE_INCOMPLETE");
    }

    @Test
    void rejectsInvertedDateRange() {
        MoneyEntryListQuery query = new MoneyEntryListQuery();
        query.setFromDate(LocalDate.of(2026, 10, 10));
        query.setToDate(LocalDate.of(2026, 10, 1));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.listEntries(USER_ID, query));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "DATE_RANGE_INVALID");
    }

    @Test
    void rejectsNegativePage() {
        MoneyEntryListQuery query = new MoneyEntryListQuery();
        query.setPage(-1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.listEntries(USER_ID, query));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "INVALID_PAGINATION");
    }

    @Test
    void rejectsZeroSize() {
        MoneyEntryListQuery query = new MoneyEntryListQuery();
        query.setSize(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.listEntries(USER_ID, query));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "INVALID_PAGINATION");
    }

    @Test
    void returnsUnconfiguredCreditCardSummary() {
        when(userRepository.findByUserId(USER_ID)).thenReturn(Optional.of(user(USER_ID)));

        CreditCardSummaryResponse summary = service.getCreditCardSummary(USER_ID);

        assertFalse(summary.getTrackingConfigured());
        assertNull(summary.getOpeningOutstandingAmount());
        assertNull(summary.getCurrentOutstandingAmount());
        assertNull(summary.getTotalPostedPurchases());
        assertNull(summary.getTotalPostedPayments());
        assertNull(summary.getTrackingStartedAtUtc());
        assertTrue(summary.getExplanation().length() > 0);
    }

    @Test
    void returnsConfiguredCreditCardSummaryWithOutstanding() {
        when(userRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(userWithCard(CARD_START, "5000.00")));
        when(moneyEntryRepository
                .sumAmountByUser_UserIdAndEntryTypeAndPaymentMethodAndStatusAndEntryAtUtcGreaterThanEqual(
                        USER_ID, EntryType.EXPENSE, PaymentMethod.CREDIT_CARD, EntryStatus.POSTED, CARD_START))
                .thenReturn(new BigDecimal("1200.00"));
        when(moneyEntryRepository.sumAmountByUser_UserIdAndEntryTypeAndStatusAndEntryAtUtcGreaterThanEqual(
                USER_ID, EntryType.CREDIT_CARD_PAYMENT, EntryStatus.POSTED, CARD_START))
                .thenReturn(new BigDecimal("300.00"));

        CreditCardSummaryResponse summary = service.getCreditCardSummary(USER_ID);

        assertTrue(summary.getTrackingConfigured());
        assertEquals(0, new BigDecimal("5000.00").compareTo(summary.getOpeningOutstandingAmount()));
        assertEquals(0, new BigDecimal("1200.00").compareTo(summary.getTotalPostedPurchases()));
        assertEquals(0, new BigDecimal("300.00").compareTo(summary.getTotalPostedPayments()));
        assertEquals(0, new BigDecimal("5900.00").compareTo(summary.getCurrentOutstandingAmount()));
        assertEquals(CARD_START, summary.getTrackingStartedAtUtc());
        assertTrue(summary.getExplanation().length() > 0);
    }

    @Test
    void setsOpeningAndTrackingStartWithoutActivity() {
        stubLockUser(user(USER_ID));
        when(moneyEntryRepository
                .sumAmountByUser_UserIdAndEntryTypeAndPaymentMethodAndStatusAndEntryAtUtcGreaterThanEqual(
                        USER_ID, EntryType.EXPENSE, PaymentMethod.CREDIT_CARD, EntryStatus.POSTED, CARD_START))
                .thenReturn(BigDecimal.ZERO);
        when(moneyEntryRepository.sumAmountByUser_UserIdAndEntryTypeAndStatusAndEntryAtUtcGreaterThanEqual(
                USER_ID, EntryType.CREDIT_CARD_PAYMENT, EntryStatus.POSTED, CARD_START))
                .thenReturn(BigDecimal.ZERO);
        CreditCardOpeningRequest request = new CreditCardOpeningRequest();
        request.setOpeningOutstandingAmount(new BigDecimal("7000.00"));
        request.setTrackingStartedAtUtc(CARD_START);

        CreditCardSummaryResponse summary = service.setCreditCardOpening(USER_ID, request);

        assertTrue(summary.getTrackingConfigured());
        assertEquals(0, new BigDecimal("7000.00").compareTo(summary.getOpeningOutstandingAmount()));
        assertEquals(0, new BigDecimal("7000.00").compareTo(summary.getCurrentOutstandingAmount()));
        assertEquals(CARD_START, summary.getTrackingStartedAtUtc());
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void defaultsTrackingStartToNowWhenOmitted() {
        stubLockUser(user(USER_ID));
        CreditCardOpeningRequest request = new CreditCardOpeningRequest();
        request.setOpeningOutstandingAmount(new BigDecimal("0.00"));

        CreditCardSummaryResponse summary = service.setCreditCardOpening(USER_ID, request);

        assertTrue(summary.getTrackingConfigured());
        assertEquals(NOW, summary.getTrackingStartedAtUtc());
        assertEquals(0, new BigDecimal("0.00").compareTo(summary.getCurrentOutstandingAmount()));
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void rejectsOpeningChangeWhenPostedActivityExists() {
        stubLockUser(userWithCard(CARD_START, "5000.00"));
        when(moneyEntryRepository
                .existsByUser_UserIdAndEntryTypeAndPaymentMethodAndStatusAndEntryAtUtcGreaterThanEqual(
                        USER_ID, EntryType.EXPENSE, PaymentMethod.CREDIT_CARD, EntryStatus.POSTED, CARD_START))
                .thenReturn(true);
        CreditCardOpeningRequest request = new CreditCardOpeningRequest();
        request.setOpeningOutstandingAmount(new BigDecimal("1000.00"));
        request.setTrackingStartedAtUtc(CARD_START);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.setCreditCardOpening(USER_ID, request));

        assertFailure(exception, HttpStatus.CONFLICT, "OPENING_BASELINE_LOCKED");
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void rejectsNegativeOpeningAmount() {
        CreditCardOpeningRequest request = new CreditCardOpeningRequest();
        request.setOpeningOutstandingAmount(new BigDecimal("-0.01"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.setCreditCardOpening(USER_ID, request));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "OPENING_AMOUNT_INVALID");
    }

    @Test
    void rejectsUnknownUserOnSummary() {
        when(userRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getCreditCardSummary(USER_ID));

        assertFailure(exception, HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
    }

    @Test
    void getEntryReturnsOwnedPostedEntry() {
        when(moneyEntryRepository.findByMoneyEntryIdAndUser_UserId(7L, USER_ID))
                .thenReturn(Optional.of(postedEntry(7L)));

        MoneyEntryResponse response = service.getEntry(USER_ID, 7L);

        assertEquals(Long.valueOf(7L), response.getMoneyEntryId());
        assertEquals(EntryStatus.POSTED, response.getStatus());
        assertEquals("GROCERIES", response.getCategoryCode());
    }

    @Test
    void rejectsGetOfMissingOrForeignEntry() {
        when(moneyEntryRepository.findByMoneyEntryIdAndUser_UserId(7L, USER_ID))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getEntry(USER_ID, 7L));

        assertFailure(exception, HttpStatus.NOT_FOUND, "ENTRY_NOT_FOUND");
    }

    @Test
    void rejectsGetWithNullEntryId() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getEntry(USER_ID, null));

        assertFailure(exception, HttpStatus.BAD_REQUEST, "REQUEST_INVALID");
    }

    private void stubLockUser(User user) {
        when(userRepository.findWithLockByUserId(USER_ID)).thenReturn(Optional.of(user));
    }

    private void stubSaveAndRefetch(long id) {
        when(moneyEntryRepository.saveAndFlush(any(MoneyEntry.class))).thenAnswer(invocation -> {
            MoneyEntry entry = invocation.getArgument(0);
            if (entry.getMoneyEntryId() == null) {
                entry.setMoneyEntryId(id);
            }
            savedRef.set(entry);
            return entry;
        });
        when(moneyEntryRepository.findById(id)).thenAnswer(invocation -> Optional.ofNullable(savedRef.get()));
    }

    private void assertFailure(BusinessException exception, HttpStatus status, String code) {
        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getCode());
    }

    private User user(Long userId) {
        User user = new User();
        user.setUserId(userId);
        user.setOpeningCreditCardOutstandingAmount(BigDecimal.ZERO);
        return user;
    }

    private User userWithCard(Instant trackingStart, String openingAmount) {
        User user = user(USER_ID);
        user.setCreditCardTrackingStartedAtUtc(trackingStart);
        user.setOpeningCreditCardOutstandingAmount(new BigDecimal(openingAmount));
        return user;
    }

    private MonthlyBudget budget(boolean investmentsEnabled) {
        MonthlyBudget budget = new MonthlyBudget();
        budget.setMonthlyBudgetId(3L);
        budget.setUser(user(USER_ID));
        budget.setBudgetMonth(LocalDate.of(2026, 10, 1));
        budget.setInvestmentsEnabled(investmentsEnabled);
        return budget;
    }

    private MoneyEntryCreateRequest expenseRequest() {
        MoneyEntryCreateRequest request = new MoneyEntryCreateRequest();
        request.setEntryDate(LocalDate.of(2026, 10, 18));
        request.setEntryTime(LocalTime.of(12, 0));
        request.setEntryType(EntryType.EXPENSE);
        request.setBucket(BudgetBucket.ESSENTIALS);
        request.setCategoryCode("GROCERIES");
        request.setAmount(new BigDecimal("250.50"));
        request.setPaymentMethod(PaymentMethod.UPI);
        return request;
    }

    private MoneyEntryCreateRequest investmentRequest() {
        MoneyEntryCreateRequest request = expenseRequest();
        request.setEntryType(EntryType.INVESTMENT);
        request.setBucket(BudgetBucket.INVESTMENTS);
        request.setCategoryCode("SIP");
        request.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        request.setAmount(new BigDecimal("5000.00"));
        return request;
    }

    private MoneyEntryCreateRequest creditCardPaymentRequest(String amount) {
        MoneyEntryCreateRequest request = expenseRequest();
        request.setEntryType(EntryType.CREDIT_CARD_PAYMENT);
        request.setBucket(null);
        request.setCategoryCode("CREDIT_CARD_BILL_PAYMENT");
        request.setPaymentMethod(PaymentMethod.UPI);
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    private MoneyEntry matchingExistingEntry(Long id, String amount) {
        MoneyEntry entry = new MoneyEntry();
        entry.setMoneyEntryId(id);
        entry.setUser(user(USER_ID));
        entry.setEntryAtUtc(ENTRY_INSTANT);
        entry.setEntryType(EntryType.EXPENSE);
        entry.setBucket(BudgetBucket.ESSENTIALS);
        entry.setCategoryCode("GROCERIES");
        entry.setAmount(new BigDecimal(amount));
        entry.setPaymentMethod(PaymentMethod.UPI);
        entry.setStatus(EntryStatus.POSTED);
        entry.setClientRequestId(UUID_1);
        return entry;
    }

    private MoneyEntry postedEntry(Long id) {
        MoneyEntry entry = new MoneyEntry();
        entry.setMoneyEntryId(id);
        entry.setUser(user(USER_ID));
        entry.setEntryAtUtc(Instant.parse("2026-10-10T06:30:00Z"));
        entry.setEntryType(EntryType.EXPENSE);
        entry.setBucket(BudgetBucket.ESSENTIALS);
        entry.setCategoryCode("GROCERIES");
        entry.setAmount(new BigDecimal("250.50"));
        entry.setPaymentMethod(PaymentMethod.UPI);
        entry.setStatus(EntryStatus.POSTED);
        return entry;
    }
}
