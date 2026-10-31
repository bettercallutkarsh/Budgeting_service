package com.budgetingapp.service;

import com.budgetingapp.dto.response.MonthlySummaryResponse;
import com.budgetingapp.dto.response.ReportPeriodResponse;
import com.budgetingapp.dto.response.ReportSummaryResponse;
import com.budgetingapp.entity.MonthlyBudget;
import com.budgetingapp.entity.enums.AllocationInputMode;
import com.budgetingapp.entity.enums.EntryType;
import com.budgetingapp.exception.BusinessException;
import com.budgetingapp.repository.MoneyEntryRepository;
import com.budgetingapp.repository.MoneyEntryRepository.PostedEntryDayTotal;
import com.budgetingapp.repository.MonthlyBudgetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Set<String> GROUP_BY_VALUES = Set.of("DAY", "MONTH", "QUARTER", "HALF_YEAR", "YEAR");
    private static final String INCOME_EXCLUDED_EXPLANATION =
            "Income is recorded monthly; choose a full-month period to include it.";
    private static final String NO_BUDGET_EXPLANATION = "Your budget isn't set for this month.";
    private static final String NO_INCOME_EXPLANATION =
            "No income is recorded for this month. You can still view entries, but budget status is limited.";
    private static final String WITHIN_EXPLANATION = "Within your monthly allocation so far.";
    private static final String OVER_EXPLANATION =
            "One or more buckets have exceeded their monthly allocation.";

    private final MonthlyBudgetRepository budgetRepository;
    private final MoneyEntryRepository moneyEntryRepository;
    private final AllocationCalculator allocationCalculator;
    private final MoneyEntryService moneyEntryService;
    private final Clock clock;

    public ReportService(MonthlyBudgetRepository budgetRepository,
                         MoneyEntryRepository moneyEntryRepository,
                         AllocationCalculator allocationCalculator,
                         MoneyEntryService moneyEntryService,
                         Clock clock) {
        this.budgetRepository = budgetRepository;
        this.moneyEntryRepository = moneyEntryRepository;
        this.allocationCalculator = allocationCalculator;
        this.moneyEntryService = moneyEntryService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MonthlySummaryResponse monthlySummary(Long userId, YearMonth month) {
        log.debug("report.monthly_summary month={}", month.format(MONTH_FORMAT));
        UtcRange range = toUtcRange(month.atDay(1), month.atEndOfMonth());
        List<PostedEntryDayTotal> dayTotals = moneyEntryRepository.sumPostedTotalsByIstDay(
                userId, range.startUtc(), range.endExclusiveUtc());
        EntryTotals totals = foldEntryTotals(dayTotals);

        MonthlySummaryResponse response = new MonthlySummaryResponse();
        response.setMonth(month.format(MONTH_FORMAT));
        response.setConsumptionExpenditure(totals.consumptionExpenditure().setScale(2));
        response.setSafetySavings(totals.safetySavings().setScale(2));
        response.setInvestmentContributions(totals.investmentContributions().setScale(2));
        response.setEssentialsUsed(totals.essentialsUsed().setScale(2));
        response.setSafetyUsed(totals.safetyUsed().setScale(2));
        response.setInvestmentsUsed(totals.investmentsUsed().setScale(2));
        response.setCreditCardOutstandingAmount(
                moneyEntryService.getCreditCardSummary(userId).getCurrentOutstandingAmount());

        MonthlyBudget budget = budgetRepository.findByUser_UserIdAndBudgetMonth(userId, month.atDay(1))
                .orElse(null);
        if (budget == null) {
            response.setStatus(MonthlySummaryResponse.MonthStatus.BUDGET_NOT_SET);
            response.setExplanation(NO_BUDGET_EXPLANATION);
            return response;
        }
        AllocationCalculator.Amounts amounts = allocationAmounts(budget);
        response.setIncomeAmount(budget.getIncomeAmount().setScale(2));
        response.setAllocationMode(budget.getAllocationMode());
        response.setInvestmentsEnabled(budget.getInvestmentsEnabled());
        response.setEssentialsPercentage(budget.getEssentialsPercentage().setScale(2));
        response.setSafetyPercentage(budget.getSafetyPercentage().setScale(2));
        response.setInvestmentsPercentage(budget.getInvestmentsPercentage().setScale(2));
        response.setEssentialsAmount(amounts.essentials());
        response.setSafetyAmount(amounts.safety());
        response.setInvestmentsAmount(amounts.investments());
        response.setEssentialsRemaining(amounts.essentials().subtract(totals.essentialsUsed()));
        response.setSafetyRemaining(amounts.safety().subtract(totals.safetyUsed()));
        response.setInvestmentsRemaining(amounts.investments().subtract(totals.investmentsUsed()));

        if (budget.getIncomeAmount().signum() == 0) {
            response.setStatus(MonthlySummaryResponse.MonthStatus.NO_INCOME);
            response.setExplanation(NO_INCOME_EXPLANATION);
            return response;
        }
        boolean over = totals.essentialsUsed().compareTo(amounts.essentials()) > 0
                || totals.safetyUsed().compareTo(amounts.safety()) > 0
                || totals.investmentsUsed().compareTo(amounts.investments()) > 0;
        response.setStatus(over
                ? MonthlySummaryResponse.MonthStatus.OVER_ALLOCATION
                : MonthlySummaryResponse.MonthStatus.WITHIN_ALLOCATION);
        response.setExplanation(over ? OVER_EXPLANATION : WITHIN_EXPLANATION);
        return response;
    }

    private AllocationCalculator.Amounts allocationAmounts(MonthlyBudget budget) {
        if (budget.getEssentialsAmount() != null
                && budget.getSafetyAmount() != null
                && budget.getInvestmentsAmount() != null) {
            return new AllocationCalculator.Amounts(
                    budget.getEssentialsAmount().setScale(2),
                    budget.getSafetyAmount().setScale(2),
                    budget.getInvestmentsAmount().setScale(2));
        }
        if (budget.getAllocationInputMode() == AllocationInputMode.AMOUNT) {
            throw new IllegalStateException("Amount-mode budgets must have persisted allocation amounts.");
        }
        AllocationCalculator.Percentages percentages = new AllocationCalculator.Percentages(
                budget.getEssentialsPercentage(), budget.getSafetyPercentage(), budget.getInvestmentsPercentage());
        return allocationCalculator.calculate(
                budget.getIncomeAmount(), percentages, budget.getInvestmentsEnabled());
    }

    @Transactional(readOnly = true)
    public ReportSummaryResponse summary(Long userId, LocalDate fromDate, LocalDate toDate, String groupBy) {
        LocalDate effectiveFrom = requireDate(fromDate, "fromDate");
        LocalDate effectiveTo = requireDate(toDate, "toDate");
        if (effectiveTo.isBefore(effectiveFrom)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REPORT_RANGE_INVALID",
                    "toDate must not be before fromDate.");
        }
        String effectiveGroupBy = requireGroupBy(groupBy);

        log.debug("report.summary fromDate={} toDate={} groupBy={}",
                effectiveFrom, effectiveTo, effectiveGroupBy);
        UtcRange range = toUtcRange(effectiveFrom, effectiveTo);
        List<PostedEntryDayTotal> dayTotals = moneyEntryRepository.sumPostedTotalsByIstDay(
                userId, range.startUtc(), range.endExclusiveUtc());
        Map<String, EntryTotals> periods = new LinkedHashMap<>();
        dayTotals.stream()
                .sorted(Comparator.comparing(PostedEntryDayTotal::getIstDay))
                .forEach(total -> {
                    String key = periodKey(total.getIstDay(), effectiveGroupBy);
                    periods.merge(key, toEntryTotals(total), EntryTotals::plus);
                });

        Map<YearMonth, BigDecimal> wholeMonthIncomes = wholeMonthIncomes(userId, effectiveFrom, effectiveTo);
        Map<String, BigDecimal> incomeByPeriod = new LinkedHashMap<>();
        if (!"DAY".equals(effectiveGroupBy)) {
            wholeMonthIncomes.forEach((month, income) ->
                    incomeByPeriod.merge(periodKey(month.atDay(1), effectiveGroupBy), income, BigDecimal::add));
        }

        List<ReportPeriodResponse> periodResponses = new ArrayList<>();
        for (Map.Entry<String, EntryTotals> entry : periods.entrySet()) {
            periodResponses.add(toPeriodResponse(entry.getKey(), entry.getValue(), incomeByPeriod));
        }
        for (Map.Entry<String, BigDecimal> entry : incomeByPeriod.entrySet()) {
            if (periods.containsKey(entry.getKey())) {
                continue;
            }
            periodResponses.add(toPeriodResponse(entry.getKey(), EntryTotals.zero(), incomeByPeriod));
        }
        periodResponses.sort(Comparator.comparing(ReportPeriodResponse::getPeriod));
        if (periodResponses.isEmpty()) {
            periodResponses.add(toPeriodResponse(
                    periodKey(effectiveFrom, effectiveGroupBy), EntryTotals.zero(), incomeByPeriod));
        }

        boolean incomeIncluded = !wholeMonthIncomes.isEmpty();
        BigDecimal totalConsumption = periodResponses.stream()
                .map(ReportPeriodResponse::getConsumptionExpenditure).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSafety = periodResponses.stream()
                .map(ReportPeriodResponse::getSafetySavings).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalInvestments = periodResponses.stream()
                .map(ReportPeriodResponse::getInvestmentContributions).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIncome = incomeIncluded
                ? wholeMonthIncomes.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                : null;

        ReportSummaryResponse response = new ReportSummaryResponse();
        response.setFromDate(effectiveFrom);
        response.setToDate(effectiveTo);
        response.setGroupBy(effectiveGroupBy);
        response.setIncomeIncluded(incomeIncluded);
        response.setExplanation(incomeIncluded ? null : INCOME_EXCLUDED_EXPLANATION);
        response.setPeriods(periodResponses);
        ReportSummaryResponse.Totals totals = new ReportSummaryResponse.Totals();
        totals.setConsumptionExpenditure(totalConsumption.setScale(2));
        totals.setSafetySavings(totalSafety.setScale(2));
        totals.setInvestmentContributions(totalInvestments.setScale(2));
        totals.setRecordedIncome(totalIncome != null ? totalIncome.setScale(2) : null);
        response.setTotals(totals);
        return response;
    }

    private ReportPeriodResponse toPeriodResponse(String period, EntryTotals entryTotals,
                                                  Map<String, BigDecimal> incomeByPeriod) {
        ReportPeriodResponse response = new ReportPeriodResponse();
        response.setPeriod(period);
        response.setConsumptionExpenditure(entryTotals.consumptionExpenditure().setScale(2));
        response.setSafetySavings(entryTotals.safetySavings().setScale(2));
        response.setInvestmentContributions(entryTotals.investmentContributions().setScale(2));
        BigDecimal income = incomeByPeriod.get(period);
        response.setRecordedIncome(income != null ? income.setScale(2) : null);
        response.setIncomeIncluded(income != null);
        return response;
    }

    private Map<YearMonth, BigDecimal> wholeMonthIncomes(Long userId, LocalDate fromDate, LocalDate toDate) {
        YearMonth startMonth = YearMonth.from(fromDate);
        YearMonth endMonth = YearMonth.from(toDate);
        YearMonth firstWhole = fromDate.equals(startMonth.atDay(1)) ? startMonth : startMonth.plusMonths(1);
        YearMonth lastWhole = toDate.equals(endMonth.atEndOfMonth()) ? endMonth : endMonth.minusMonths(1);
        Map<YearMonth, BigDecimal> incomes = new TreeMap<>();
        if (firstWhole.isAfter(lastWhole)) {
            return incomes;
        }
        YearMonth currentMonth = YearMonth.now(clock.withZone(BUSINESS_ZONE));
        for (MonthlyBudget budget : budgetRepository.findByUser_UserIdOrderByBudgetMonthAsc(userId)) {
            YearMonth month = YearMonth.from(budget.getBudgetMonth());
            if (month.isBefore(firstWhole) || month.isAfter(lastWhole) || month.isAfter(currentMonth)) {
                continue;
            }
            incomes.put(month, budget.getIncomeAmount());
        }
        return incomes;
    }

    private String periodKey(LocalDate istDay, String groupBy) {
        YearMonth month = YearMonth.from(istDay);
        int year = istDay.getYear();
        int monthOfYear = istDay.getMonthValue();
        return switch (groupBy) {
            case "DAY" -> istDay.toString();
            case "MONTH" -> month.toString();
            case "QUARTER" -> fyLabel(fyStartYear(monthOfYear, year)) + quarterSuffix(monthOfYear);
            case "HALF_YEAR" -> fyLabel(fyStartYear(monthOfYear, year)) + halfSuffix(monthOfYear);
            case "YEAR" -> fyLabel(fyStartYear(monthOfYear, year));
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "REPORT_GROUP_BY_INVALID",
                    "groupBy must be one of DAY, MONTH, QUARTER, HALF_YEAR or YEAR.");
        };
    }

    private int fyStartYear(int monthOfYear, int year) {
        return monthOfYear >= 4 ? year : year - 1;
    }

    private String fyLabel(int fyStartYear) {
        return "FY" + fyStartYear + "-" + String.format("%02d", (fyStartYear + 1) % 100);
    }

    private String quarterSuffix(int monthOfYear) {
        if (monthOfYear >= 4 && monthOfYear <= 6) {
            return "Q1";
        }
        if (monthOfYear >= 7 && monthOfYear <= 9) {
            return "Q2";
        }
        if (monthOfYear >= 10) {
            return "Q3";
        }
        return "Q4";
    }

    private String halfSuffix(int monthOfYear) {
        return monthOfYear >= 4 && monthOfYear <= 9 ? "H1" : "H2";
    }

    private EntryTotals foldEntryTotals(List<PostedEntryDayTotal> dayTotals) {
        EntryTotals totals = EntryTotals.zero();
        for (PostedEntryDayTotal total : dayTotals) {
            totals = totals.plus(toEntryTotals(total));
        }
        return totals;
    }

    private EntryTotals toEntryTotals(PostedEntryDayTotal total) {
        EntryType entryType = EntryType.valueOf(total.getEntryType());
        String bucket = total.getBucket();
        BigDecimal amount = total.getTotal();
        return switch (entryType) {
            case EXPENSE -> "ESSENTIALS".equals(bucket)
                    ? new EntryTotals(amount, BigDecimal.ZERO, BigDecimal.ZERO, amount, BigDecimal.ZERO, BigDecimal.ZERO)
                    : new EntryTotals(amount, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, amount, BigDecimal.ZERO);
            case SAFETY_SAVING -> new EntryTotals(BigDecimal.ZERO, amount, BigDecimal.ZERO,
                    BigDecimal.ZERO, amount, BigDecimal.ZERO);
            case INVESTMENT -> new EntryTotals(BigDecimal.ZERO, BigDecimal.ZERO, amount,
                    BigDecimal.ZERO, BigDecimal.ZERO, amount);
            case CREDIT_CARD_PAYMENT -> EntryTotals.zero();
        };
    }

    private LocalDate requireDate(LocalDate date, String field) {
        if (date == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REPORT_RANGE_INCOMPLETE",
                    field + " is required.");
        }
        return date;
    }

    private String requireGroupBy(String groupBy) {
        if (groupBy == null || groupBy.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REPORT_GROUP_BY_INVALID",
                    "groupBy is required and must be one of DAY, MONTH, QUARTER, HALF_YEAR or YEAR.");
        }
        String normalized = groupBy.trim().toUpperCase();
        if (!GROUP_BY_VALUES.contains(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REPORT_GROUP_BY_INVALID",
                    "groupBy must be one of DAY, MONTH, QUARTER, HALF_YEAR or YEAR.");
        }
        return normalized;
    }

    private UtcRange toUtcRange(LocalDate fromDate, LocalDate toDate) {
        Instant startUtc = fromDate.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant endExclusiveUtc = toDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        return new UtcRange(startUtc, endExclusiveUtc);
    }

    record EntryTotals(BigDecimal consumptionExpenditure, BigDecimal safetySavings,
                       BigDecimal investmentContributions, BigDecimal essentialsUsed,
                       BigDecimal safetyUsed, BigDecimal investmentsUsed) {

        static EntryTotals zero() {
            return new EntryTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        EntryTotals plus(EntryTotals other) {
            return new EntryTotals(
                    consumptionExpenditure.add(other.consumptionExpenditure),
                    safetySavings.add(other.safetySavings),
                    investmentContributions.add(other.investmentContributions),
                    essentialsUsed.add(other.essentialsUsed),
                    safetyUsed.add(other.safetyUsed),
                    investmentsUsed.add(other.investmentsUsed));
        }
    }

    record UtcRange(Instant startUtc, Instant endExclusiveUtc) {
    }
}
