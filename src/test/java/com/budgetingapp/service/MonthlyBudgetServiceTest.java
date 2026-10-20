package com.budgetingapp.service;

import com.budgetingapp.dto.request.MonthlyBudgetSaveRequest;
import com.budgetingapp.dto.response.MonthlyBudgetResponse;
import com.budgetingapp.entity.MonthlyBudget;
import com.budgetingapp.entity.User;
import com.budgetingapp.entity.enums.AllocationMode;
import com.budgetingapp.entity.enums.EntryType;
import com.budgetingapp.entity.enums.IncomeType;
import com.budgetingapp.exception.AllocationValidationException;
import com.budgetingapp.exception.BusinessException;
import com.budgetingapp.exception.InvestmentToggleBlockedException;
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
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyBudgetServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate OCTOBER = LocalDate.of(2026, 10, 1);
    private static final YearMonth OCTOBER_MONTH = YearMonth.of(2026, 10);

    @Mock
    private MonthlyBudgetRepository budgetRepository;

    @Mock
    private MoneyEntryRepository moneyEntryRepository;

    @Mock
    private UserRepository userRepository;

    private MonthlyBudgetService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-18T10:00:00Z"), ZoneId.of("Asia/Kolkata"));
        service = new MonthlyBudgetService(budgetRepository, moneyEntryRepository, userRepository,
                new AllocationCalculator(), clock);
    }

    @Test
    void createsCurrentMonthBudgetWithPresetAndDerivedAmounts() {
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, OCTOBER)).thenReturn(Optional.empty());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        AtomicReference<MonthlyBudget> savedRef = new AtomicReference<>();
        when(budgetRepository.saveAndFlush(any(MonthlyBudget.class))).thenAnswer(invocation -> {
            MonthlyBudget budget = invocation.getArgument(0);
            budget.setMonthlyBudgetId(7L);
            savedRef.set(budget);
            return budget;
        });
        when(budgetRepository.findById(7L)).thenAnswer(invocation -> Optional.ofNullable(savedRef.get()));

        MonthlyBudgetResponse response = service.saveBudget(USER_ID, OCTOBER_MONTH, balancedRequest());

        assertEquals("2026-10", response.getMonth());
        assertEquals(AllocationMode.BALANCED, response.getAllocationMode());
        assertEquals(0, new BigDecimal("50000.00").compareTo(response.getIncomeAmount()));
        assertEquals(0, new BigDecimal("55.00").compareTo(response.getEssentialsPercentage()));
        assertEquals(0, new BigDecimal("10.00").compareTo(response.getSafetyPercentage()));
        assertEquals(0, new BigDecimal("35.00").compareTo(response.getInvestmentsPercentage()));
        assertEquals(0, new BigDecimal("27500.00").compareTo(response.getEssentialsAmount()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(response.getSafetyAmount()));
        assertEquals(0, new BigDecimal("17500.00").compareTo(response.getInvestmentsAmount()));
        assertEquals(2, response.getEssentialsAmount().scale());
        assertNull(response.getCreatedAtUtc());

        ArgumentCaptor<MonthlyBudget> captor = ArgumentCaptor.forClass(MonthlyBudget.class);
        verify(budgetRepository).saveAndFlush(captor.capture());
        assertEquals(OCTOBER, captor.getValue().getBudgetMonth());
        assertEquals(USER_ID, captor.getValue().getUser().getUserId());
        assertEquals(IncomeType.SALARY, captor.getValue().getIncomeType());
        verifyNoInteractions(moneyEntryRepository);
    }

    @Test
    void updatesExistingBudgetInPlace() {
        MonthlyBudget existing = new MonthlyBudget();
        existing.setMonthlyBudgetId(9L);
        existing.setUser(user(USER_ID));
        existing.setBudgetMonth(OCTOBER);
        existing.setIncomeType(IncomeType.SALARY);
        existing.setIncomeAmount(new BigDecimal("10000.00"));
        existing.setAllocationMode(AllocationMode.CONSERVATIVE);
        existing.setInvestmentsEnabled(Boolean.TRUE);
        existing.setEssentialsPercentage(new BigDecimal("60.00"));
        existing.setSafetyPercentage(new BigDecimal("10.00"));
        existing.setInvestmentsPercentage(new BigDecimal("30.00"));

        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, OCTOBER))
                .thenReturn(Optional.of(existing));
        when(budgetRepository.saveAndFlush(existing)).thenReturn(existing);
        when(budgetRepository.findById(9L)).thenReturn(Optional.of(existing));

        MonthlyBudgetSaveRequest request = balancedRequest();
        request.setIncomeAmount(new BigDecimal("40000.00"));
        request.setAllocationMode(AllocationMode.AGGRESSIVE);

        MonthlyBudgetResponse response = service.saveBudget(USER_ID, OCTOBER_MONTH, request);

        assertEquals("2026-10", response.getMonth());
        assertEquals(AllocationMode.AGGRESSIVE, response.getAllocationMode());
        assertEquals(0, new BigDecimal("40000.00").compareTo(response.getIncomeAmount()));
        assertEquals(0, new BigDecimal("50.00").compareTo(response.getEssentialsPercentage()));
        assertEquals(0, new BigDecimal("20000.00").compareTo(response.getEssentialsAmount()));
        assertEquals(0, new BigDecimal("4000.00").compareTo(response.getSafetyAmount()));
        assertEquals(0, new BigDecimal("16000.00").compareTo(response.getInvestmentsAmount()));
        assertEquals(0, new BigDecimal("50.00").compareTo(existing.getEssentialsPercentage()));
        verify(userRepository, never()).findById(any());
    }

    @Test
    void rejectsPastMonthWithPeriodReadOnly() {
        MonthlyBudgetSaveRequest request = balancedRequest();

        BusinessException ex = assertThrows(PeriodReadOnlyException.class, () ->
                service.saveBudget(USER_ID, YearMonth.of(2026, 9), request));

        assertEquals("PERIOD_READ_ONLY", ex.getCode());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verifyNoInteractions(budgetRepository, moneyEntryRepository, userRepository);
    }

    @Test
    void allowsFutureMonthBudgetCreation() {
        YearMonth january = YearMonth.of(2027, 1);
        LocalDate januaryFirst = LocalDate.of(2027, 1, 1);
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, januaryFirst)).thenReturn(Optional.empty());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        AtomicReference<MonthlyBudget> savedRef = new AtomicReference<>();
        when(budgetRepository.saveAndFlush(any(MonthlyBudget.class))).thenAnswer(invocation -> {
            MonthlyBudget budget = invocation.getArgument(0);
            budget.setMonthlyBudgetId(11L);
            savedRef.set(budget);
            return budget;
        });
        when(budgetRepository.findById(11L)).thenAnswer(invocation -> Optional.ofNullable(savedRef.get()));

        MonthlyBudgetResponse response = service.saveBudget(USER_ID, january, balancedRequest());

        assertEquals("2027-01", response.getMonth());
    }

    @Test
    void blocksInvestmentsOffWhenPostedInvestmentExists() {
        when(moneyEntryRepository
                .existsByUser_UserIdAndEntryTypeAndEntryAtUtcGreaterThanEqualAndEntryAtUtcLessThan(
                        eq(USER_ID), eq(EntryType.INVESTMENT), any(Instant.class), any(Instant.class)))
                .thenReturn(true);
        MonthlyBudgetSaveRequest request = investmentsOffRequest();

        BusinessException ex = assertThrows(InvestmentToggleBlockedException.class, () ->
                service.saveBudget(USER_ID, OCTOBER_MONTH, request));

        assertEquals("INVESTMENT_TOGGLE_BLOCKED", ex.getCode());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(budgetRepository, never()).saveAndFlush(any());

        ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(moneyEntryRepository)
                .existsByUser_UserIdAndEntryTypeAndEntryAtUtcGreaterThanEqualAndEntryAtUtcLessThan(
                        eq(USER_ID), eq(EntryType.INVESTMENT), startCaptor.capture(), endCaptor.capture());
        assertEquals(Instant.parse("2026-09-30T18:30:00Z"), startCaptor.getValue());
        assertEquals(Instant.parse("2026-10-31T18:30:00Z"), endCaptor.getValue());
    }

    @Test
    void allowsInvestmentsOffWhenNoPostedInvestments() {
        when(moneyEntryRepository
                .existsByUser_UserIdAndEntryTypeAndEntryAtUtcGreaterThanEqualAndEntryAtUtcLessThan(
                        eq(USER_ID), eq(EntryType.INVESTMENT), any(Instant.class), any(Instant.class)))
                .thenReturn(false);
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, OCTOBER)).thenReturn(Optional.empty());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        AtomicReference<MonthlyBudget> savedRef = new AtomicReference<>();
        when(budgetRepository.saveAndFlush(any(MonthlyBudget.class))).thenAnswer(invocation -> {
            MonthlyBudget budget = invocation.getArgument(0);
            budget.setMonthlyBudgetId(12L);
            savedRef.set(budget);
            return budget;
        });
        when(budgetRepository.findById(12L)).thenAnswer(invocation -> Optional.ofNullable(savedRef.get()));

        MonthlyBudgetResponse response = service.saveBudget(USER_ID, OCTOBER_MONTH, investmentsOffRequest());

        assertEquals(AllocationMode.MANUAL, response.getAllocationMode());
        assertEquals(Boolean.FALSE, response.getInvestmentsEnabled());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getInvestmentsPercentage()));
        assertEquals(0, new BigDecimal("20000.00").compareTo(response.getIncomeAmount()));
        assertEquals(0, new BigDecimal("14000.00").compareTo(response.getEssentialsAmount()));
        assertEquals(0, new BigDecimal("6000.00").compareTo(response.getSafetyAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getInvestmentsAmount()));
    }

    @Test
    void rejectsPresetWithClientSubmittedPercentages() {
        MonthlyBudgetSaveRequest request = balancedRequest();
        request.setEssentialsPercentage(new BigDecimal("50.00"));
        request.setSafetyPercentage(new BigDecimal("10.00"));
        request.setInvestmentsPercentage(new BigDecimal("40.00"));

        BusinessException ex = assertThrows(AllocationValidationException.class, () ->
                service.saveBudget(USER_ID, OCTOBER_MONTH, request));

        assertEquals("ALLOCATION_PRESET_MISMATCH", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(budgetRepository, moneyEntryRepository, userRepository);
    }

    @Test
    void rejectsManualAllocationNotTotallingHundred() {
        MonthlyBudgetSaveRequest request = balancedRequest();
        request.setAllocationMode(AllocationMode.MANUAL);
        request.setEssentialsPercentage(new BigDecimal("55.00"));
        request.setSafetyPercentage(new BigDecimal("10.00"));
        request.setInvestmentsPercentage(new BigDecimal("30.00"));

        BusinessException ex = assertThrows(AllocationValidationException.class, () ->
                service.saveBudget(USER_ID, OCTOBER_MONTH, request));

        assertEquals("ALLOCATION_TOTAL_INVALID", ex.getCode());
        verifyNoInteractions(budgetRepository, moneyEntryRepository, userRepository);
    }

    @Test
    void rejectsInvestmentsOffWithPresetMode() {
        MonthlyBudgetSaveRequest request = balancedRequest();
        request.setAllocationMode(AllocationMode.CONSERVATIVE);
        request.setInvestmentsEnabled(Boolean.FALSE);
        request.setEssentialsPercentage(new BigDecimal("70.00"));
        request.setSafetyPercentage(new BigDecimal("30.00"));
        request.setInvestmentsPercentage(BigDecimal.ZERO);

        BusinessException ex = assertThrows(AllocationValidationException.class, () ->
                service.saveBudget(USER_ID, OCTOBER_MONTH, request));

        assertEquals("INVESTMENTS_OFF_REQUIRES_MANUAL", ex.getCode());
        verifyNoInteractions(budgetRepository, moneyEntryRepository, userRepository);
    }

    @Test
    void rejectsNegativeIncome() {
        MonthlyBudgetSaveRequest request = balancedRequest();
        request.setIncomeAmount(new BigDecimal("-0.01"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.saveBudget(USER_ID, OCTOBER_MONTH, request));

        assertEquals("INCOME_INVALID", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(budgetRepository, moneyEntryRepository, userRepository);
    }

    @Test
    void rejectsIncomeWithMoreThanTwoDecimals() {
        MonthlyBudgetSaveRequest request = balancedRequest();
        request.setIncomeAmount(new BigDecimal("30000.005"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.saveBudget(USER_ID, OCTOBER_MONTH, request));

        assertEquals("INCOME_PRECISION", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(budgetRepository, moneyEntryRepository, userRepository);
    }

    @Test
    void rejectsIncomeSourceLabelForNonOtherIncomeType() {
        MonthlyBudgetSaveRequest request = balancedRequest();
        request.setIncomeType(IncomeType.SALARY);
        request.setIncomeSourceLabel("Freelance");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.saveBudget(USER_ID, OCTOBER_MONTH, request));

        assertEquals("INVALID_INCOME_SOURCE_LABEL", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(budgetRepository, moneyEntryRepository, userRepository);
    }

    @Test
    void storesTrimmedLabelForOtherIncomeType() {
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, OCTOBER)).thenReturn(Optional.empty());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        AtomicReference<MonthlyBudget> savedRef = new AtomicReference<>();
        when(budgetRepository.saveAndFlush(any(MonthlyBudget.class))).thenAnswer(invocation -> {
            MonthlyBudget budget = invocation.getArgument(0);
            budget.setMonthlyBudgetId(13L);
            savedRef.set(budget);
            return budget;
        });
        when(budgetRepository.findById(13L)).thenAnswer(invocation -> Optional.ofNullable(savedRef.get()));

        MonthlyBudgetSaveRequest request = balancedRequest();
        request.setIncomeType(IncomeType.OTHER_INCOME);
        request.setIncomeSourceLabel("  Freelance  ");

        MonthlyBudgetResponse response = service.saveBudget(USER_ID, OCTOBER_MONTH, request);

        assertEquals("Freelance", response.getIncomeSourceLabel());
        assertEquals(IncomeType.OTHER_INCOME, response.getIncomeType());
    }

    @Test
    void allowsZeroIncomeBudget() {
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, OCTOBER)).thenReturn(Optional.empty());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        AtomicReference<MonthlyBudget> savedRef = new AtomicReference<>();
        when(budgetRepository.saveAndFlush(any(MonthlyBudget.class))).thenAnswer(invocation -> {
            MonthlyBudget budget = invocation.getArgument(0);
            budget.setMonthlyBudgetId(14L);
            savedRef.set(budget);
            return budget;
        });
        when(budgetRepository.findById(14L)).thenAnswer(invocation -> Optional.ofNullable(savedRef.get()));

        MonthlyBudgetSaveRequest request = balancedRequest();
        request.setIncomeAmount(new BigDecimal("0.00"));

        MonthlyBudgetResponse response = service.saveBudget(USER_ID, OCTOBER_MONTH, request);

        assertEquals(0, BigDecimal.ZERO.compareTo(response.getIncomeAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getEssentialsAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getSafetyAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getInvestmentsAmount()));
    }

    @Test
    void mapsDuplicateCreationToConflict() {
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, OCTOBER)).thenReturn(Optional.empty());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(budgetRepository.saveAndFlush(any(MonthlyBudget.class)))
                .thenThrow(new DataIntegrityViolationException("uq_monthly_budgets_user_month"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.saveBudget(USER_ID, OCTOBER_MONTH, balancedRequest()));

        assertEquals("BUDGET_ALREADY_EXISTS", ex.getCode());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void getBudgetReturnsEmptyWhenNoBudgetExists() {
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, OCTOBER)).thenReturn(Optional.empty());

        Optional<MonthlyBudgetResponse> result = service.getBudget(USER_ID, OCTOBER_MONTH);

        assertTrue(result.isEmpty());
        verify(budgetRepository).findByUser_UserIdAndBudgetMonth(USER_ID, OCTOBER);
    }

    @Test
    void getBudgetReturnsResponseScopedToUserAndMonth() {
        MonthlyBudget budget = new MonthlyBudget();
        budget.setMonthlyBudgetId(21L);
        budget.setUser(user(USER_ID));
        budget.setBudgetMonth(OCTOBER);
        budget.setIncomeType(IncomeType.SALARY);
        budget.setIncomeAmount(new BigDecimal("10000.01"));
        budget.setAllocationMode(AllocationMode.BALANCED);
        budget.setInvestmentsEnabled(Boolean.TRUE);
        budget.setEssentialsPercentage(new BigDecimal("55.00"));
        budget.setSafetyPercentage(new BigDecimal("10.00"));
        budget.setInvestmentsPercentage(new BigDecimal("35.00"));
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, OCTOBER)).thenReturn(Optional.of(budget));

        Optional<MonthlyBudgetResponse> result = service.getBudget(USER_ID, OCTOBER_MONTH);

        assertTrue(result.isPresent());
        MonthlyBudgetResponse response = result.get();
        assertEquals("2026-10", response.getMonth());
        assertEquals(0, new BigDecimal("5500.01").compareTo(response.getEssentialsAmount()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(response.getSafetyAmount()));
        assertEquals(0, new BigDecimal("3500.00").compareTo(response.getInvestmentsAmount()));
        verify(budgetRepository).findByUser_UserIdAndBudgetMonth(USER_ID, OCTOBER);
    }

    @Test
    void getBudgetIsNeverServedForAnotherUserScope() {
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(99L, OCTOBER)).thenReturn(Optional.empty());

        Optional<MonthlyBudgetResponse> result = service.getBudget(99L, OCTOBER_MONTH);

        assertTrue(result.isEmpty());
        verify(budgetRepository).findByUser_UserIdAndBudgetMonth(99L, OCTOBER);
        verify(budgetRepository, never()).findByUser_UserIdAndBudgetMonth(eq(USER_ID), any(LocalDate.class));
    }

    private MonthlyBudgetSaveRequest balancedRequest() {
        MonthlyBudgetSaveRequest request = new MonthlyBudgetSaveRequest();
        request.setIncomeType(IncomeType.SALARY);
        request.setIncomeAmount(new BigDecimal("50000.00"));
        request.setAllocationMode(AllocationMode.BALANCED);
        request.setInvestmentsEnabled(Boolean.TRUE);
        return request;
    }

    private MonthlyBudgetSaveRequest investmentsOffRequest() {
        MonthlyBudgetSaveRequest request = new MonthlyBudgetSaveRequest();
        request.setIncomeType(IncomeType.SALARY);
        request.setIncomeAmount(new BigDecimal("20000.00"));
        request.setAllocationMode(AllocationMode.MANUAL);
        request.setInvestmentsEnabled(Boolean.FALSE);
        request.setEssentialsPercentage(new BigDecimal("70.00"));
        request.setSafetyPercentage(new BigDecimal("30.00"));
        return request;
    }

    private User user(Long userId) {
        User user = new User();
        user.setUserId(userId);
        return user;
    }
}
