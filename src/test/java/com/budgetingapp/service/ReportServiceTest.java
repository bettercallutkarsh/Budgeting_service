package com.budgetingapp.service;

import com.budgetingapp.dto.response.CreditCardSummaryResponse;
import com.budgetingapp.dto.response.MonthlySummaryResponse;
import com.budgetingapp.dto.response.ReportPeriodResponse;
import com.budgetingapp.dto.response.ReportSummaryResponse;
import com.budgetingapp.entity.MonthlyBudget;
import com.budgetingapp.entity.enums.AllocationInputMode;
import com.budgetingapp.entity.enums.AllocationMode;
import com.budgetingapp.exception.BusinessException;
import com.budgetingapp.repository.MoneyEntryRepository;
import com.budgetingapp.repository.MoneyEntryRepository.PostedEntryDayTotal;
import com.budgetingapp.repository.MonthlyBudgetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final Long USER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");
    private static final Instant OCT_START_UTC = Instant.parse("2026-09-30T18:30:00Z");
    private static final Instant NOV_START_UTC = Instant.parse("2026-10-31T18:30:00Z");

    @Mock
    private MonthlyBudgetRepository budgetRepository;

    @Mock
    private MoneyEntryRepository moneyEntryRepository;

    @Mock
    private MoneyEntryService moneyEntryService;

    private ReportService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Kolkata"));
        service = new ReportService(budgetRepository, moneyEntryRepository,
                new AllocationCalculator(), moneyEntryService, clock);
    }

    @Test
    void monthlySummaryWithoutBudgetReturnsBudgetNotSetStatusWithUsageOnly() {
        when(moneyEntryRepository.sumPostedTotalsByIstDay(USER_ID, OCT_START_UTC, NOV_START_UTC))
                .thenReturn(List.of(
                        row("2026-10-05", "EXPENSE", "ESSENTIALS", "1200.00"),
                        row("2026-10-06", "EXPENSE", "SAFETY", "300.00"),
                        row("2026-10-07", "SAFETY_SAVING", "SAFETY_SAVING", "200.00"),
                        row("2026-10-08", "INVESTMENT", "INVESTMENTS", "1000.00")));
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.empty());
        stubCardSummary("4500.00");

        MonthlySummaryResponse response = service.monthlySummary(USER_ID, YearMonth.of(2026, 10));

        assertEquals("2026-10", response.getMonth());
        assertEquals(MonthlySummaryResponse.MonthStatus.BUDGET_NOT_SET, response.getStatus());
        assertNull(response.getIncomeAmount());
        assertEquals(new BigDecimal("1200.00"), response.getEssentialsUsed());
        assertEquals(new BigDecimal("500.00"), response.getSafetyUsed());
        assertEquals(new BigDecimal("1000.00"), response.getInvestmentsUsed());
        assertEquals(new BigDecimal("1500.00"), response.getConsumptionExpenditure());
        assertEquals(new BigDecimal("200.00"), response.getSafetySavings());
        assertEquals(new BigDecimal("1000.00"), response.getInvestmentContributions());
        assertEquals(new BigDecimal("4500.00"), response.getCreditCardOutstandingAmount());
        assertNotNull(response.getExplanation());
        verify(moneyEntryRepository).sumPostedTotalsByIstDay(eq(USER_ID), eq(OCT_START_UTC), eq(NOV_START_UTC));
    }

    @Test
    void monthlySummaryWithIncomeReportsWithinAllocation() {
        when(moneyEntryRepository.sumPostedTotalsByIstDay(USER_ID, OCT_START_UTC, NOV_START_UTC))
                .thenReturn(List.of(
                        row("2026-10-05", "EXPENSE", "ESSENTIALS", "1000.00"),
                        row("2026-10-06", "EXPENSE", "SAFETY", "500.00"),
                        row("2026-10-07", "SAFETY_SAVING", "SAFETY_SAVING", "200.00"),
                        row("2026-10-08", "INVESTMENT", "INVESTMENTS", "1000.00")));
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.of(budget("2026-10", "30000.00")));
        stubCardSummary("0.00");

        MonthlySummaryResponse response = service.monthlySummary(USER_ID, YearMonth.of(2026, 10));

        assertEquals(MonthlySummaryResponse.MonthStatus.WITHIN_ALLOCATION, response.getStatus());
        assertEquals(new BigDecimal("30000.00"), response.getIncomeAmount());
        assertEquals(AllocationMode.BALANCED, response.getAllocationMode());
        assertEquals(new BigDecimal("16500.00"), response.getEssentialsAmount());
        assertEquals(new BigDecimal("3000.00"), response.getSafetyAmount());
        assertEquals(new BigDecimal("10500.00"), response.getInvestmentsAmount());
        assertEquals(new BigDecimal("1000.00"), response.getEssentialsUsed());
        assertEquals(new BigDecimal("700.00"), response.getSafetyUsed());
        assertEquals(new BigDecimal("1000.00"), response.getInvestmentsUsed());
        assertEquals(new BigDecimal("15500.00"), response.getEssentialsRemaining());
        assertEquals(new BigDecimal("2300.00"), response.getSafetyRemaining());
        assertEquals(new BigDecimal("9500.00"), response.getInvestmentsRemaining());
        assertNotNull(response.getExplanation());
    }

    @Test
    void monthlySummaryUsesPersistedAmountModeAmountsInsteadOfRecalculatingFromPercentages() {
        when(moneyEntryRepository.sumPostedTotalsByIstDay(USER_ID, OCT_START_UTC, NOV_START_UTC))
                .thenReturn(List.of(row("2026-10-31", "INVESTMENT", "INVESTMENTS", "2000.00")));
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.of(amountModeBudget()));
        stubCardSummary("0.00");

        MonthlySummaryResponse response = service.monthlySummary(USER_ID, YearMonth.of(2026, 10));

        assertEquals(MonthlySummaryResponse.MonthStatus.WITHIN_ALLOCATION, response.getStatus());
        assertEquals(new BigDecimal("40800.00"), response.getIncomeAmount());
        assertEquals(new BigDecimal("34720.00"), response.getEssentialsAmount());
        assertEquals(new BigDecimal("4080.00"), response.getSafetyAmount());
        assertEquals(new BigDecimal("2000.00"), response.getInvestmentsAmount());
        assertEquals(new BigDecimal("2000.00"), response.getInvestmentsUsed());
        assertEquals(new BigDecimal("0.00"), response.getInvestmentsRemaining());
    }

    @Test
    void monthlySummaryFlagsOverAllocationWhenAnyBucketExceedsLimit() {
        when(moneyEntryRepository.sumPostedTotalsByIstDay(USER_ID, OCT_START_UTC, NOV_START_UTC))
                .thenReturn(List.of(row("2026-10-05", "EXPENSE", "ESSENTIALS", "17000.00")));
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.of(budget("2026-10", "30000.00")));
        stubCardSummary("0.00");

        MonthlySummaryResponse response = service.monthlySummary(USER_ID, YearMonth.of(2026, 10));

        assertEquals(MonthlySummaryResponse.MonthStatus.OVER_ALLOCATION, response.getStatus());
        assertEquals(new BigDecimal("-500.00"), response.getEssentialsRemaining());
    }

    @Test
    void monthlySummaryWithZeroIncomeReportsNoIncomeStatus() {
        when(moneyEntryRepository.sumPostedTotalsByIstDay(USER_ID, OCT_START_UTC, NOV_START_UTC))
                .thenReturn(List.of());
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.of(budget("2026-10", "0.00")));
        stubCardSummary("0.00");

        MonthlySummaryResponse response = service.monthlySummary(USER_ID, YearMonth.of(2026, 10));

        assertEquals(MonthlySummaryResponse.MonthStatus.NO_INCOME, response.getStatus());
        assertEquals(new BigDecimal("0.00"), response.getIncomeAmount());
        assertEquals(new BigDecimal("0.00"), response.getEssentialsAmount());
        assertEquals(new BigDecimal("0.00"), response.getSafetyAmount());
        assertEquals(new BigDecimal("0.00"), response.getInvestmentsAmount());
        assertNotNull(response.getExplanation());
    }

    @Test
    void monthlySummaryKeepsCardOutstandingNullWhenCardSummaryHasNoCurrentAmount() {
        when(moneyEntryRepository.sumPostedTotalsByIstDay(USER_ID, OCT_START_UTC, NOV_START_UTC))
                .thenReturn(List.of());
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.empty());
        stubCardSummary(null);

        MonthlySummaryResponse response = service.monthlySummary(USER_ID, YearMonth.of(2026, 10));

        assertNull(response.getCreditCardOutstandingAmount());
    }

    @Test
    void monthlySummaryIgnoresCreditCardPaymentsAndFoldsSafetyBucketIntoUsed() {
        when(moneyEntryRepository.sumPostedTotalsByIstDay(USER_ID, OCT_START_UTC, NOV_START_UTC))
                .thenReturn(List.of(
                        row("2026-10-05", "EXPENSE", "SAFETY", "500.00"),
                        row("2026-10-06", "CREDIT_CARD_PAYMENT", "SAFETY", "2000.00")));
        when(budgetRepository.findByUser_UserIdAndBudgetMonth(USER_ID, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.empty());
        stubCardSummary("0.00");

        MonthlySummaryResponse response = service.monthlySummary(USER_ID, YearMonth.of(2026, 10));

        assertEquals(new BigDecimal("500.00"), response.getConsumptionExpenditure());
        assertEquals(new BigDecimal("500.00"), response.getSafetyUsed());
        assertEquals(new BigDecimal("0.00"), response.getSafetySavings());
        assertEquals(new BigDecimal("0.00"), response.getEssentialsUsed());
        assertEquals(new BigDecimal("0.00"), response.getInvestmentsUsed());
    }

    @Test
    void summaryRequiresBothDates() {
        BusinessException missingFrom = assertThrows(BusinessException.class,
                () -> service.summary(USER_ID, null, LocalDate.of(2026, 10, 31), "MONTH"));
        assertEquals(HttpStatus.BAD_REQUEST, missingFrom.getStatus());
        assertEquals("REPORT_RANGE_INCOMPLETE", missingFrom.getCode());

        BusinessException missingTo = assertThrows(BusinessException.class,
                () -> service.summary(USER_ID, LocalDate.of(2026, 10, 1), null, "MONTH"));
        assertEquals("REPORT_RANGE_INCOMPLETE", missingTo.getCode());
    }

    @Test
    void summaryRejectsInvertedRange() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.summary(USER_ID, LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1), "MONTH"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("REPORT_RANGE_INVALID", exception.getCode());
    }

    @Test
    void summaryRequiresKnownGroupByValue() {
        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.summary(USER_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null));
        assertEquals("REPORT_GROUP_BY_INVALID", missing.getCode());

        BusinessException unknown = assertThrows(BusinessException.class,
                () -> service.summary(USER_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "WEEK"));
        assertEquals("REPORT_GROUP_BY_INVALID", unknown.getCode());
    }

    @Test
    void summaryDayGroupingOmitsIncomePerPeriodButTotalsIncludeWholeMonth() {
        when(moneyEntryRepository.sumPostedTotalsByIstDay(USER_ID, OCT_START_UTC, NOV_START_UTC))
                .thenReturn(List.of(
                        row("2026-10-17", "EXPENSE", "ESSENTIALS", "100.00"),
                        row("2026-10-18", "EXPENSE", "SAFETY", "50.00")));
        when(budgetRepository.findByUser_UserIdOrderByBudgetMonthAsc(USER_ID))
                .thenReturn(List.of(budget("2026-10", "30000.00")));

        ReportSummaryResponse response = service.summary(USER_ID,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), " day ");

        assertEquals("DAY", response.getGroupBy());
        assertTrue(response.getIncomeIncluded());
        assertNull(response.getExplanation());
        assertEquals(2, response.getPeriods().size());
        ReportPeriodResponse first = response.getPeriods().get(0);
        assertEquals("2026-10-17", first.getPeriod());
        assertEquals(new BigDecimal("100.00"), first.getConsumptionExpenditure());
        assertNull(first.getRecordedIncome());
        assertEquals(Boolean.FALSE, first.getIncomeIncluded());
        ReportPeriodResponse second = response.getPeriods().get(1);
        assertEquals("2026-10-18", second.getPeriod());
        assertEquals(new BigDecimal("50.00"), second.getConsumptionExpenditure());
        assertNull(second.getRecordedIncome());
        assertEquals(new BigDecimal("150.00"), response.getTotals().getConsumptionExpenditure());
        assertEquals(new BigDecimal("30000.00"), response.getTotals().getRecordedIncome());
        verify(moneyEntryRepository).sumPostedTotalsByIstDay(eq(USER_ID), eq(OCT_START_UTC), eq(NOV_START_UTC));
    }

    @Test
    void summaryMonthGroupingExcludesFutureAndPartialMonthsFromIncome() {
        when(moneyEntryRepository.sumPostedTotalsByIstDay(USER_ID, OCT_START_UTC,
                        Instant.parse("2026-11-30T18:30:00Z")))
                .thenReturn(List.of(row("2026-11-05", "EXPENSE", "ESSENTIALS", "200.00")));
        when(budgetRepository.findByUser_UserIdOrderByBudgetMonthAsc(USER_ID))
                .thenReturn(List.of(
                        budget("2026-10", "30000.00"),
                        budget("2026-11", "25000.00")));

        ReportSummaryResponse response = service.summary(USER_ID,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30), "MONTH");

        assertEquals(2, response.getPeriods().size());
        ReportPeriodResponse october = response.getPeriods().get(0);
        assertEquals("2026-10", october.getPeriod());
        assertEquals(new BigDecimal("30000.00"), october.getRecordedIncome());
        assertEquals(Boolean.TRUE, october.getIncomeIncluded());
        ReportPeriodResponse november = response.getPeriods().get(1);
        assertEquals("2026-11", november.getPeriod());
        assertEquals(new BigDecimal("200.00"), november.getConsumptionExpenditure());
        assertNull(november.getRecordedIncome());
        assertEquals(Boolean.FALSE, november.getIncomeIncluded());
        assertTrue(response.getIncomeIncluded());
        assertEquals(new BigDecimal("30000.00"), response.getTotals().getRecordedIncome());
        assertEquals(new BigDecimal("200.00"), response.getTotals().getConsumptionExpenditure());
    }

    @Test
    void summaryQuarterGroupingSpansFinancialYearBoundary() {
        when(moneyEntryRepository.sumPostedTotalsByIstDay(USER_ID,
                        Instant.parse("2026-02-28T18:30:00Z"), Instant.parse("2026-04-30T18:30:00Z")))
                .thenReturn(List.of(
                        row("2026-03-31", "EXPENSE", "ESSENTIALS", "100.00"),
                        row("2026-04-01", "EXPENSE", "ESSENTIALS", "200.00")));
        when(budgetRepository.findByUser_UserIdOrderByBudgetMonthAsc(USER_ID))
                .thenReturn(List.of());

        ReportSummaryResponse response = service.summary(USER_ID,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 30), "QUARTER");

        assertEquals(2, response.getPeriods().size());
        assertEquals("FY2025-26Q4", response.getPeriods().get(0).getPeriod());
        assertEquals(new BigDecimal("100.00"), response.getPeriods().get(0).getConsumptionExpenditure());
        assertEquals("FY2026-27Q1", response.getPeriods().get(1).getPeriod());
        assertEquals(new BigDecimal("200.00"), response.getPeriods().get(1).getConsumptionExpenditure());
        assertEquals(Boolean.FALSE, response.getIncomeIncluded());
        assertNotNull(response.getExplanation());
    }

    @Test
    void summaryHalfYearAndYearGroupingLabelsFinancialYearPeriods() {
        when(moneyEntryRepository.sumPostedTotalsByIstDay(USER_ID,
                        Instant.parse("2026-08-31T18:30:00Z"), Instant.parse("2026-10-31T18:30:00Z")))
                .thenReturn(List.of(
                        row("2026-09-30", "EXPENSE", "ESSENTIALS", "100.00"),
                        row("2026-10-01", "EXPENSE", "ESSENTIALS", "200.00")));
        when(budgetRepository.findByUser_UserIdOrderByBudgetMonthAsc(USER_ID))
                .thenReturn(List.of());

        ReportSummaryResponse halfYears = service.summary(USER_ID,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31), "HALF_YEAR");
        assertEquals(2, halfYears.getPeriods().size());
        assertEquals("FY2026-27H1", halfYears.getPeriods().get(0).getPeriod());
        assertEquals(new BigDecimal("100.00"), halfYears.getPeriods().get(0).getConsumptionExpenditure());
        assertEquals("FY2026-27H2", halfYears.getPeriods().get(1).getPeriod());
        assertEquals(new BigDecimal("200.00"), halfYears.getPeriods().get(1).getConsumptionExpenditure());

        ReportSummaryResponse years = service.summary(USER_ID,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31), "YEAR");
        assertEquals(1, years.getPeriods().size());
        assertEquals("FY2026-27", years.getPeriods().get(0).getPeriod());
        assertEquals(new BigDecimal("300.00"), years.getPeriods().get(0).getConsumptionExpenditure());
    }

    @Test
    void summaryMidMonthRangeOmitsIncomeEntirely() {
        when(moneyEntryRepository.sumPostedTotalsByIstDay(USER_ID,
                        Instant.parse("2026-10-09T18:30:00Z"), Instant.parse("2026-10-20T18:30:00Z")))
                .thenReturn(List.of(row("2026-10-12", "EXPENSE", "ESSENTIALS", "100.00")));

        ReportSummaryResponse response = service.summary(USER_ID,
                LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 20), "MONTH");

        assertEquals(Boolean.FALSE, response.getIncomeIncluded());
        assertNotNull(response.getExplanation());
        assertNull(response.getTotals().getRecordedIncome());
        assertEquals(1, response.getPeriods().size());
        assertEquals("2026-10", response.getPeriods().get(0).getPeriod());
        assertNull(response.getPeriods().get(0).getRecordedIncome());
    }

    @Test
    void summaryWithNoDataReturnsSingleZeroPeriod() {
        when(moneyEntryRepository.sumPostedTotalsByIstDay(USER_ID, OCT_START_UTC, NOV_START_UTC))
                .thenReturn(List.of());
        when(budgetRepository.findByUser_UserIdOrderByBudgetMonthAsc(USER_ID))
                .thenReturn(List.of());

        ReportSummaryResponse response = service.summary(USER_ID,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "MONTH");

        assertEquals(1, response.getPeriods().size());
        assertEquals("2026-10", response.getPeriods().get(0).getPeriod());
        assertEquals(new BigDecimal("0.00"), response.getPeriods().get(0).getConsumptionExpenditure());
        assertEquals(Boolean.FALSE, response.getIncomeIncluded());
        assertEquals(new BigDecimal("0.00"), response.getTotals().getConsumptionExpenditure());
        assertNull(response.getTotals().getRecordedIncome());
    }

    private void stubCardSummary(String amount) {
        CreditCardSummaryResponse summary = new CreditCardSummaryResponse();
        summary.setCurrentOutstandingAmount(amount != null ? new BigDecimal(amount) : null);
        when(moneyEntryService.getCreditCardSummary(USER_ID)).thenReturn(summary);
    }

    private MonthlyBudget budget(String month, String income) {
        BigDecimal incomeAmount = new BigDecimal(income);
        AllocationCalculator.Amounts amounts = new AllocationCalculator().calculate(
                incomeAmount,
                new AllocationCalculator.Percentages(
                        new BigDecimal("55.00"), new BigDecimal("10.00"), new BigDecimal("35.00")),
                Boolean.TRUE);
        MonthlyBudget budget = new MonthlyBudget();
        budget.setMonthlyBudgetId(1L);
        budget.setBudgetMonth(LocalDate.parse(month + "-01"));
        budget.setIncomeAmount(incomeAmount);
        budget.setAllocationMode(AllocationMode.BALANCED);
        budget.setAllocationInputMode(AllocationInputMode.PERCENTAGE);
        budget.setInvestmentsEnabled(Boolean.TRUE);
        budget.setEssentialsPercentage(new BigDecimal("55.00"));
        budget.setSafetyPercentage(new BigDecimal("10.00"));
        budget.setInvestmentsPercentage(new BigDecimal("35.00"));
        budget.setEssentialsAmount(amounts.essentials());
        budget.setSafetyAmount(amounts.safety());
        budget.setInvestmentsAmount(amounts.investments());
        return budget;
    }

    private MonthlyBudget amountModeBudget() {
        MonthlyBudget budget = new MonthlyBudget();
        budget.setMonthlyBudgetId(2L);
        budget.setBudgetMonth(LocalDate.of(2026, 10, 1));
        budget.setIncomeAmount(new BigDecimal("40800.00"));
        budget.setAllocationMode(AllocationMode.BALANCED);
        budget.setAllocationInputMode(AllocationInputMode.AMOUNT);
        budget.setInvestmentsEnabled(Boolean.TRUE);
        budget.setEssentialsPercentage(new BigDecimal("85.10"));
        budget.setSafetyPercentage(new BigDecimal("10.00"));
        budget.setInvestmentsPercentage(new BigDecimal("4.90"));
        budget.setEssentialsAmount(new BigDecimal("34720.00"));
        budget.setSafetyAmount(new BigDecimal("4080.00"));
        budget.setInvestmentsAmount(new BigDecimal("2000.00"));
        return budget;
    }

    private PostedEntryDayTotal row(String istDay, String entryType, String bucket, String total) {
        return new PostedEntryDayTotal() {
            @Override
            public LocalDate getIstDay() {
                return LocalDate.parse(istDay);
            }

            @Override
            public String getEntryType() {
                return entryType;
            }

            @Override
            public String getBucket() {
                return bucket;
            }

            @Override
            public BigDecimal getTotal() {
                return new BigDecimal(total);
            }
        };
    }
}
