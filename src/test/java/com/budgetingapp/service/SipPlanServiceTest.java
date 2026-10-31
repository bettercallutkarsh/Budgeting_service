package com.budgetingapp.service;

import com.budgetingapp.dto.request.SipInstallmentPaymentRequest;
import com.budgetingapp.dto.request.SipPlanCreateRequest;
import com.budgetingapp.dto.request.SipPlanStopRequest;
import com.budgetingapp.dto.request.SipPlanUpdateRequest;
import com.budgetingapp.dto.response.SipCommitmentSummaryResponse;
import com.budgetingapp.dto.response.SipInstallmentListResponse;
import com.budgetingapp.dto.response.SipInstallmentPaymentResponse;
import com.budgetingapp.dto.response.SipPlanListResponse;
import com.budgetingapp.dto.response.SipPlanResponse;
import com.budgetingapp.entity.MoneyEntry;
import com.budgetingapp.entity.MonthlyBudget;
import com.budgetingapp.entity.SipPlan;
import com.budgetingapp.entity.User;
import com.budgetingapp.entity.enums.BudgetBucket;
import com.budgetingapp.entity.enums.EntryStatus;
import com.budgetingapp.entity.enums.EntryType;
import com.budgetingapp.entity.enums.SipInstallmentStatus;
import com.budgetingapp.entity.enums.SipPlanStatus;
import com.budgetingapp.exception.BusinessException;
import com.budgetingapp.repository.MoneyEntryRepository;
import com.budgetingapp.repository.MonthlyBudgetRepository;
import com.budgetingapp.repository.SipPlanRepository;
import com.budgetingapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SipPlanServiceTest {

    private static final Long USER_ID = 5L;
    private static final Long PLAN_ID = 11L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 31);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-31T06:00:00Z"), ZoneId.of("Asia/Kolkata"));

    @Mock
    private SipPlanRepository sipPlanRepository;

    @Mock
    private MoneyEntryRepository moneyEntryRepository;

    @Mock
    private MonthlyBudgetRepository budgetRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private SipPlanService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new SipPlanService(sipPlanRepository, moneyEntryRepository, budgetRepository,
                userRepository, new SipScheduleCalculator(), transactionManager, CLOCK);
    }

    @Test
    void createPlanPersistsNormalizedActivePlan() {
        when(userRepository.findWithLockByUserId(USER_ID)).thenReturn(Optional.of(user()));
        AtomicReference<SipPlan> saved = new AtomicReference<>();
        when(sipPlanRepository.saveAndFlush(any(SipPlan.class))).thenAnswer(invocation -> {
            SipPlan plan = invocation.getArgument(0);
            plan.setSipPlanId(PLAN_ID);
            saved.set(plan);
            return plan;
        });
        when(sipPlanRepository.findById(PLAN_ID)).thenAnswer(invocation -> Optional.of(saved.get()));

        SipPlanResponse response = service.createPlan(USER_ID, createRequest("  Retirement SIP  "));

        assertEquals(PLAN_ID, response.getSipPlanId());
        assertEquals("SIP", response.getInvestmentType());
        assertEquals("Retirement SIP", response.getPlanName());
        assertEquals(0, new BigDecimal("5000.00").compareTo(response.getMonthlyAmount()));
        assertEquals(SipPlanStatus.ACTIVE, response.getStatus());
        assertEquals(LocalDate.of(2026, 10, 5), response.getTrackingStartDate());
    }

    @Test
    void createRejectsTrackingStartBeforeOriginalStart() {
        SipPlanCreateRequest request = createRequest("SIP");
        request.setOriginalStartDate(LocalDate.of(2026, 10, 10));
        request.setTrackingStartDate(LocalDate.of(2026, 10, 1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createPlan(USER_ID, request));

        assertEquals("TRACKING_START_BEFORE_ORIGINAL", ex.getCode());
        verify(sipPlanRepository, never()).saveAndFlush(any());
    }

    @Test
    void listPlansDerivesCompletedAndSumsOnlyActiveCommitments() {
        SipPlan active = plan(PLAN_ID, "Active", "5000.00",
                LocalDate.of(2026, 10, 1), null);
        SipPlan completed = plan(12L, "Completed", "3000.00",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 30));
        when(sipPlanRepository.findByUser_UserIdOrderByCreatedAtUtcDescSipPlanIdDesc(USER_ID))
                .thenReturn(List.of(active, completed));

        SipPlanListResponse response = service.listPlans(USER_ID);

        assertEquals(2, response.getContent().size());
        assertEquals(SipPlanStatus.ACTIVE, response.getContent().get(0).getStatus());
        assertEquals(SipPlanStatus.COMPLETED, response.getContent().get(1).getStatus());
        assertEquals(0, new BigDecimal("5000.00").compareTo(response.getActiveMonthlyCommitment()));
    }

    @Test
    void getPlanRejectsAnotherUsersPlan() {
        when(sipPlanRepository.findBySipPlanIdAndUser_UserId(PLAN_ID, USER_ID))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getPlan(USER_ID, PLAN_ID));

        assertEquals("SIP_PLAN_NOT_FOUND", ex.getCode());
    }

    @Test
    void updatePlanChangesEditableFieldsWhileActive() {
        SipPlan plan = plan(PLAN_ID, "Old", "5000.00",
                LocalDate.of(2026, 10, 1), null);
        when(userRepository.findWithLockByUserId(USER_ID)).thenReturn(Optional.of(user()));
        when(sipPlanRepository.findBySipPlanIdAndUser_UserId(PLAN_ID, USER_ID))
                .thenReturn(Optional.of(plan));
        when(moneyEntryRepository.existsBySipPlan_SipPlanIdAndStatus(PLAN_ID, EntryStatus.POSTED))
                .thenReturn(false);
        when(sipPlanRepository.saveAndFlush(any(SipPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(sipPlanRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));

        SipPlanUpdateRequest request = updateRequest("New name", "7500.00",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5), 20);
        SipPlanResponse response = service.updatePlan(USER_ID, PLAN_ID, request);

        assertEquals("New name", response.getPlanName());
        assertEquals(0, new BigDecimal("7500.00").compareTo(response.getMonthlyAmount()));
        assertEquals(20, response.getInstallmentDay());
    }

    @Test
    void updateRejectsStartDateChangeAfterPayment() {
        SipPlan plan = plan(PLAN_ID, "Old", "5000.00",
                LocalDate.of(2026, 10, 1), null);
        when(userRepository.findWithLockByUserId(USER_ID)).thenReturn(Optional.of(user()));
        when(sipPlanRepository.findBySipPlanIdAndUser_UserId(PLAN_ID, USER_ID))
                .thenReturn(Optional.of(plan));
        when(moneyEntryRepository.existsBySipPlan_SipPlanIdAndStatus(PLAN_ID, EntryStatus.POSTED))
                .thenReturn(true);

        SipPlanUpdateRequest request = updateRequest("Old", "5000.00",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15), 5);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updatePlan(USER_ID, PLAN_ID, request));

        assertEquals("SIP_PLAN_DATES_LOCKED", ex.getCode());
        verify(sipPlanRepository, never()).saveAndFlush(any());
    }

    @Test
    void stopPlanStoresActualEndDateWithoutChangingPlannedEnd() {
        SipPlan plan = plan(PLAN_ID, "SIP", "5000.00",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31));
        when(userRepository.findWithLockByUserId(USER_ID)).thenReturn(Optional.of(user()));
        when(sipPlanRepository.findBySipPlanIdAndUser_UserId(PLAN_ID, USER_ID))
                .thenReturn(Optional.of(plan));
        when(sipPlanRepository.saveAndFlush(any(SipPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(sipPlanRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));

        SipPlanStopRequest request = new SipPlanStopRequest();
        request.setStopDate(LocalDate.of(2026, 10, 20));
        SipPlanResponse response = service.stopPlan(USER_ID, PLAN_ID, request);

        assertEquals(SipPlanStatus.STOPPED, response.getStatus());
        assertEquals(LocalDate.of(2026, 10, 20), response.getActualEndedDate());
        assertEquals(LocalDate.of(2026, 12, 31), response.getPlannedEndDate());
    }

    @Test
    void stopRejectsFutureDate() {
        SipPlan plan = plan(PLAN_ID, "SIP", "5000.00",
                LocalDate.of(2026, 10, 1), null);
        when(userRepository.findWithLockByUserId(USER_ID)).thenReturn(Optional.of(user()));
        when(sipPlanRepository.findBySipPlanIdAndUser_UserId(PLAN_ID, USER_ID))
                .thenReturn(Optional.of(plan));

        SipPlanStopRequest request = new SipPlanStopRequest();
        request.setStopDate(TODAY.plusDays(1));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.stopPlan(USER_ID, PLAN_ID, request));

        assertEquals("SIP_STOP_DATE_FUTURE", ex.getCode());
    }

    @Test
    void listInstallmentsMarksPaidPeriodAndClampsMonthlyDates() {
        SipPlan plan = plan(PLAN_ID, "SIP", "5000.00",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31));
        MoneyEntry paid = paidEntry(plan, LocalDate.of(2026, 10, 1), "5000.00");
        when(sipPlanRepository.findByUser_UserIdOrderByCreatedAtUtcDescSipPlanIdDesc(USER_ID))
                .thenReturn(List.of(plan));
        when(moneyEntryRepository
                .findByUser_UserIdAndSipPlanIsNotNullAndStatusAndSipInstallmentPeriodBetween(
                        USER_ID, EntryStatus.POSTED, LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 12, 1)))
                .thenReturn(List.of(paid));

        SipInstallmentListResponse response = service.listInstallments(USER_ID, null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31));

        assertEquals(3, response.getContent().size());
        assertEquals(SipInstallmentStatus.PAID, response.getContent().get(0).getStatus());
        assertEquals(77L, response.getContent().get(0).getPaidMoneyEntryId());
        assertEquals(SipInstallmentStatus.UPCOMING, response.getContent().get(1).getStatus());
        assertEquals("2026-11", response.getContent().get(1).getPeriod());
    }

    @Test
    void listInstallmentsRejectsRangeLongerThanSixtyMonths() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.listInstallments(USER_ID, null,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2031, 1, 1)));

        assertEquals("DATE_RANGE_TOO_LARGE", ex.getCode());
    }

    @Test
    void markPaidCreatesLinkedInvestmentEntry() {
        SipPlan plan = plan(PLAN_ID, "SIP", "5000.00",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31));
        when(userRepository.findWithLockByUserId(USER_ID)).thenReturn(Optional.of(user()));
        when(sipPlanRepository.findBySipPlanIdAndUser_UserId(PLAN_ID, USER_ID))
                .thenReturn(Optional.of(plan));
        when(moneyEntryRepository
                .findByUser_UserIdAndSipPlan_SipPlanIdAndSipInstallmentPeriodAndStatus(
                        USER_ID, PLAN_ID, LocalDate.of(2026, 10, 1), EntryStatus.POSTED))
                .thenReturn(Optional.empty());
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.of(budget("10000.00", true)));
        AtomicReference<MoneyEntry> saved = new AtomicReference<>();
        when(moneyEntryRepository.saveAndFlush(any(MoneyEntry.class))).thenAnswer(invocation -> {
            MoneyEntry entry = invocation.getArgument(0);
            entry.setMoneyEntryId(77L);
            saved.set(entry);
            return entry;
        });
        when(moneyEntryRepository.findById(77L)).thenAnswer(invocation -> Optional.of(saved.get()));
        SipInstallmentPaymentRequest request = new SipInstallmentPaymentRequest();
        request.setPaymentDate(LocalDate.of(2026, 10, 5));
        request.setPaymentTime(LocalTime.of(10, 0));

        SipInstallmentPaymentResponse response = service.markPaid(
                USER_ID, PLAN_ID, "2026-10", request);

        assertFalse(response.getIdempotentReplay());
        assertNotNull(response.getMoneyEntry());
        assertEquals(77L, response.getMoneyEntry().getMoneyEntryId());
        assertEquals(PLAN_ID, response.getMoneyEntry().getSipPlanId());
        assertEquals(LocalDate.of(2026, 10, 1), response.getMoneyEntry().getSipInstallmentPeriod());
        assertEquals(EntryType.INVESTMENT, response.getMoneyEntry().getEntryType());
        assertEquals(BudgetBucket.INVESTMENTS, response.getMoneyEntry().getBucket());
        assertEquals("SIP", response.getMoneyEntry().getCategoryCode());
        assertEquals(0, new BigDecimal("5000.00").compareTo(response.getMoneyEntry().getAmount()));
        assertEquals(SipInstallmentStatus.PAID, response.getInstallment().getStatus());
    }

    @Test
    void markPaidReturnsExistingEntryAsIdempotentReplay() {
        SipPlan plan = plan(PLAN_ID, "SIP", "5000.00",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31));
        MoneyEntry paid = paidEntry(plan, LocalDate.of(2026, 10, 1), "5000.00");
        when(userRepository.findWithLockByUserId(USER_ID)).thenReturn(Optional.of(user()));
        when(sipPlanRepository.findBySipPlanIdAndUser_UserId(PLAN_ID, USER_ID))
                .thenReturn(Optional.of(plan));
        when(moneyEntryRepository
                .findByUser_UserIdAndSipPlan_SipPlanIdAndSipInstallmentPeriodAndStatus(
                        USER_ID, PLAN_ID, LocalDate.of(2026, 10, 1), EntryStatus.POSTED))
                .thenReturn(Optional.of(paid));

        SipInstallmentPaymentResponse response = service.markPaid(
                USER_ID, PLAN_ID, "2026-10", new SipInstallmentPaymentRequest());

        assertTrue(response.getIdempotentReplay());
        assertEquals(77L, response.getMoneyEntry().getMoneyEntryId());
        verify(moneyEntryRepository, never()).saveAndFlush(any());
    }

    @Test
    void markPaidRecoversConcurrentDuplicateAsReplay() {
        SipPlan plan = plan(PLAN_ID, "SIP", "5000.00",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31));
        MoneyEntry paid = paidEntry(plan, LocalDate.of(2026, 10, 1), "5000.00");
        when(userRepository.findWithLockByUserId(USER_ID)).thenReturn(Optional.of(user()));
        when(sipPlanRepository.findBySipPlanIdAndUser_UserId(PLAN_ID, USER_ID))
                .thenReturn(Optional.of(plan));
        when(moneyEntryRepository
                .findByUser_UserIdAndSipPlan_SipPlanIdAndSipInstallmentPeriodAndStatus(
                        USER_ID, PLAN_ID, LocalDate.of(2026, 10, 1), EntryStatus.POSTED))
                .thenReturn(Optional.empty(), Optional.of(paid));
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.of(budget("10000.00", true)));
        when(moneyEntryRepository.saveAndFlush(any(MoneyEntry.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        SipInstallmentPaymentResponse response = service.markPaid(
                USER_ID, PLAN_ID, "2026-10", new SipInstallmentPaymentRequest());

        assertTrue(response.getIdempotentReplay());
        assertEquals(77L, response.getMoneyEntry().getMoneyEntryId());
    }

    @Test
    void markPaidRejectsPeriodOutsideSchedule() {
        SipPlan plan = plan(PLAN_ID, "SIP", "5000.00",
                LocalDate.of(2026, 10, 10), null);
        when(userRepository.findWithLockByUserId(USER_ID)).thenReturn(Optional.of(user()));
        when(sipPlanRepository.findBySipPlanIdAndUser_UserId(PLAN_ID, USER_ID))
                .thenReturn(Optional.of(plan));
        when(moneyEntryRepository
                .findByUser_UserIdAndSipPlan_SipPlanIdAndSipInstallmentPeriodAndStatus(
                        USER_ID, PLAN_ID, LocalDate.of(2026, 10, 1), EntryStatus.POSTED))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.markPaid(USER_ID, PLAN_ID, "2026-10",
                        new SipInstallmentPaymentRequest()));

        assertEquals("SIP_INSTALLMENT_NOT_SCHEDULED", ex.getCode());
    }

    @Test
    void commitmentSummaryFlagsOverAllocation() {
        SipPlan plan = plan(PLAN_ID, "SIP", "5000.00",
                LocalDate.of(2026, 10, 1), null);
        when(sipPlanRepository.findByUser_UserIdOrderByCreatedAtUtcDescSipPlanIdDesc(USER_ID))
                .thenReturn(List.of(plan));
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.of(budget("4000.00", true)));

        SipCommitmentSummaryResponse response = service.getCommitmentSummary(USER_ID, "2026-10");

        assertEquals("2026-10", response.getMonth());
        assertEquals(0, new BigDecimal("5000.00").compareTo(response.getActiveMonthlyCommitment()));
        assertEquals(0, new BigDecimal("4000.00").compareTo(response.getInvestmentsAmount()));
        assertFalse(response.getWithinAllocation());
        assertEquals(0, new BigDecimal("1000.00").compareTo(response.getOverAllocationAmount()));
        assertEquals(1, response.getActivePlanCount());
    }

    @Test
    void commitmentSummaryWithNoBudgetReturnsNullComparison() {
        when(sipPlanRepository.findByUser_UserIdOrderByCreatedAtUtcDescSipPlanIdDesc(USER_ID))
                .thenReturn(List.of());
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.empty());

        SipCommitmentSummaryResponse response = service.getCommitmentSummary(USER_ID, "2026-10");

        assertNull(response.getWithinAllocation());
        assertNull(response.getInvestmentsAmount());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getActiveMonthlyCommitment()));
    }

    private SipPlanCreateRequest createRequest(String name) {
        SipPlanCreateRequest request = new SipPlanCreateRequest();
        request.setPlanName(name);
        request.setMonthlyAmount(new BigDecimal("5000.00"));
        request.setInstallmentDay(5);
        request.setOriginalStartDate(LocalDate.of(2026, 9, 1));
        request.setTrackingStartDate(LocalDate.of(2026, 10, 5));
        return request;
    }

    private SipPlanUpdateRequest updateRequest(String name, String amount, LocalDate original,
                                               LocalDate tracking, int day) {
        SipPlanUpdateRequest request = new SipPlanUpdateRequest();
        request.setPlanName(name);
        request.setMonthlyAmount(new BigDecimal(amount));
        request.setInstallmentDay(day);
        request.setOriginalStartDate(original);
        request.setTrackingStartDate(tracking);
        return request;
    }

    private SipPlan plan(Long id, String name, String amount, LocalDate tracking, LocalDate plannedEnd) {
        SipPlan plan = new SipPlan();
        plan.setSipPlanId(id);
        plan.setPlanName(name);
        plan.setMonthlyAmount(new BigDecimal(amount));
        plan.setInstallmentDay(5);
        plan.setOriginalStartDate(LocalDate.of(2026, 9, 1));
        plan.setTrackingStartDate(tracking);
        plan.setPlannedEndDate(plannedEnd);
        plan.setStatus(SipPlanStatus.ACTIVE);
        return plan;
    }

    private MoneyEntry paidEntry(SipPlan plan, LocalDate period, String amount) {
        MoneyEntry entry = new MoneyEntry();
        entry.setMoneyEntryId(77L);
        entry.setUser(plan.getUser());
        entry.setSipPlan(plan);
        entry.setSipInstallmentPeriod(period);
        entry.setEntryAtUtc(Instant.parse("2026-10-05T04:30:00Z"));
        entry.setEntryType(EntryType.INVESTMENT);
        entry.setBucket(BudgetBucket.INVESTMENTS);
        entry.setCategoryCode("SIP");
        entry.setAmount(new BigDecimal(amount));
        entry.setStatus(EntryStatus.POSTED);
        return entry;
    }

    private MonthlyBudget budget(String investmentsAmount, boolean enabled) {
        MonthlyBudget budget = new MonthlyBudget();
        budget.setInvestmentsEnabled(enabled);
        budget.setInvestmentsAmount(new BigDecimal(investmentsAmount));
        return budget;
    }

    private User user() {
        User user = new User();
        user.setUserId(USER_ID);
        return user;
    }
}
