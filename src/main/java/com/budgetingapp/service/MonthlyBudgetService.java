package com.budgetingapp.service;

import com.budgetingapp.dto.request.MonthlyBudgetSaveRequest;
import com.budgetingapp.dto.response.MonthlyBudgetResponse;
import com.budgetingapp.entity.MonthlyBudget;
import com.budgetingapp.entity.User;
import com.budgetingapp.entity.enums.AllocationInputMode;
import com.budgetingapp.entity.enums.AllocationMode;
import com.budgetingapp.entity.enums.EntryType;
import com.budgetingapp.entity.enums.IncomeType;
import com.budgetingapp.exception.BusinessException;
import com.budgetingapp.exception.InvestmentToggleBlockedException;
import com.budgetingapp.exception.PeriodReadOnlyException;
import com.budgetingapp.repository.MoneyEntryRepository;
import com.budgetingapp.repository.MonthlyBudgetRepository;
import com.budgetingapp.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.Optional;

@Service
public class MonthlyBudgetService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyBudgetService.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MonthlyBudgetRepository budgetRepository;
    private final MoneyEntryRepository moneyEntryRepository;
    private final UserRepository userRepository;
    private final AllocationCalculator allocationCalculator;
    private final Clock clock;

    public MonthlyBudgetService(MonthlyBudgetRepository budgetRepository,
                                MoneyEntryRepository moneyEntryRepository,
                                UserRepository userRepository,
                                AllocationCalculator allocationCalculator,
                                Clock clock) {
        this.budgetRepository = budgetRepository;
        this.moneyEntryRepository = moneyEntryRepository;
        this.userRepository = userRepository;
        this.allocationCalculator = allocationCalculator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<MonthlyBudgetResponse> getBudget(Long userId, YearMonth month) {
        return budgetRepository.findByUser_UserIdAndBudgetMonth(userId, month.atDay(1))
                .map(this::toResponse);
    }

    @Transactional
    public MonthlyBudgetResponse saveBudget(Long userId, YearMonth month, MonthlyBudgetSaveRequest request) {
        requireRequestShape(request);
        if (month.isBefore(YearMonth.now(clock))) {
            throw new PeriodReadOnlyException(month.format(MONTH_FORMAT));
        }
        BigDecimal income = normalizeIncome(request.getIncomeAmount());
        String incomeSourceLabel = normalizeLabel(request.getIncomeType(), request.getIncomeSourceLabel());
        boolean investmentsEnabled = Boolean.TRUE.equals(request.getInvestmentsEnabled());
        AllocationInputMode inputMode = request.getAllocationInputMode() == null
                ? AllocationInputMode.PERCENTAGE
                : request.getAllocationInputMode();

        AllocationCalculator.Percentages percentages;
        AllocationCalculator.Amounts amounts;

        if (inputMode == AllocationInputMode.AMOUNT) {
            if (!investmentsEnabled && request.getAllocationMode() != AllocationMode.MANUAL) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVESTMENTS_OFF_REQUIRES_MANUAL",
                        "Choose Manual allocation when Investments are disabled.");
            }
            if (investmentsEnabled) {
                amounts = allocationCalculator.calculateAmounts(income,
                        request.getEssentialsAmount(), request.getSafetyAmount(), request.getInvestmentsAmount());
            } else {
                if (request.getInvestmentsAmount() != null
                        && request.getInvestmentsAmount().compareTo(BigDecimal.ZERO) != 0) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "INVESTMENTS_MUST_BE_ZERO",
                            "investmentsAmount must be 0.00 when Investments are disabled.");
                }
                if (request.getEssentialsAmount() == null || request.getSafetyAmount() == null) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "ALLOCATION_AMOUNT_INVALID",
                            "essentialsAmount and safetyAmount are required when Investments are disabled.");
                }
                amounts = allocationCalculator.calculateAmounts(income,
                        request.getEssentialsAmount(), request.getSafetyAmount(), BigDecimal.ZERO.setScale(2));
                if (amounts.investments().compareTo(BigDecimal.ZERO) != 0
                        || amounts.essentials().add(amounts.safety()).compareTo(income) != 0) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "ALLOCATION_AMOUNT_INVALID",
                            "Essentials and Safety amounts must total the income amount when Investments are disabled.");
                }
            }
            percentages = allocationCalculator.percentagesFromAmounts(income,
                    amounts.essentials(), amounts.safety(), amounts.investments());
            if (!investmentsEnabled) {
                assertNoPostedInvestments(userId, month);
            }
        } else {
            percentages = allocationCalculator.resolve(
                    request.getAllocationMode(), investmentsEnabled,
                    request.getEssentialsPercentage(), request.getSafetyPercentage(), request.getInvestmentsPercentage());
            amounts = allocationCalculator.calculate(income, percentages, investmentsEnabled);
            if (!investmentsEnabled) {
                assertNoPostedInvestments(userId, month);
            }
        }

        MonthlyBudget budget = budgetRepository
                .findByUser_UserIdAndBudgetMonth(userId, month.atDay(1))
                .orElseGet(() -> newBudget(userId, month.atDay(1)));
        budget.setIncomeType(request.getIncomeType());
        budget.setIncomeSourceLabel(incomeSourceLabel);
        budget.setIncomeAmount(income);
        budget.setAllocationInputMode(inputMode);
        budget.setAllocationMode(request.getAllocationMode());
        budget.setInvestmentsEnabled(investmentsEnabled);
        budget.setEssentialsPercentage(percentages.essentials());
        budget.setSafetyPercentage(percentages.safety());
        budget.setInvestmentsPercentage(percentages.investments());
        budget.setEssentialsAmount(amounts.essentials());
        budget.setSafetyAmount(amounts.safety());
        budget.setInvestmentsAmount(amounts.investments());

        MonthlyBudget saved;
        try {
            saved = budgetRepository.saveAndFlush(budget);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(HttpStatus.CONFLICT, "BUDGET_ALREADY_EXISTS",
                    "A budget already exists for " + month.format(MONTH_FORMAT) + ".");
        }
        MonthlyBudget fresh = saved;
        if (saved.getMonthlyBudgetId() != null) {
            fresh = budgetRepository.findById(saved.getMonthlyBudgetId()).orElse(saved);
        }
        log.info("budget.saved month={} inputMode={} allocationMode={} investmentsEnabled={}",
                month.format(MONTH_FORMAT), inputMode, budget.getAllocationMode(), investmentsEnabled);
        return toResponse(fresh);
    }

    private void requireRequestShape(MonthlyBudgetSaveRequest request) {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "Request body is required.");
        }
        if (request.getIncomeType() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "incomeType is required.");
        }
        if (request.getIncomeAmount() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "incomeAmount is required.");
        }
        if (request.getAllocationMode() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "allocationMode is required.");
        }
        if (request.getInvestmentsEnabled() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "investmentsEnabled is required.");
        }
    }

    private BigDecimal normalizeIncome(BigDecimal income) {
        if (income.signum() < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INCOME_INVALID",
                    "incomeAmount must not be negative.");
        }
        if (income.scale() > 2) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INCOME_PRECISION",
                    "incomeAmount must have at most 2 decimal places.");
        }
        return income.setScale(2);
    }

    private String normalizeLabel(IncomeType incomeType, String incomeSourceLabel) {
        boolean provided = incomeSourceLabel != null && !incomeSourceLabel.isBlank();
        if (provided && incomeType != IncomeType.OTHER_INCOME) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_INCOME_SOURCE_LABEL",
                    "incomeSourceLabel is only allowed when incomeType is OTHER_INCOME.");
        }
        return provided ? incomeSourceLabel.trim() : null;
    }

    private void assertNoPostedInvestments(Long userId, YearMonth month) {
        Instant startUtc = month.atDay(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant endUtc = month.plusMonths(1).atDay(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        boolean investmentExists = moneyEntryRepository
                .existsByUser_UserIdAndEntryTypeAndEntryAtUtcGreaterThanEqualAndEntryAtUtcLessThan(
                        userId, EntryType.INVESTMENT, startUtc, endUtc);
        if (investmentExists) {
            throw new InvestmentToggleBlockedException();
        }
    }

    private MonthlyBudget newBudget(Long userId, LocalDate budgetMonth) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                        "User not found."));
        MonthlyBudget budget = new MonthlyBudget();
        budget.setUser(user);
        budget.setBudgetMonth(budgetMonth);
        return budget;
    }

    private MonthlyBudgetResponse toResponse(MonthlyBudget budget) {
        MonthlyBudgetResponse response = new MonthlyBudgetResponse();
        response.setMonth(budget.getBudgetMonth().format(MONTH_FORMAT));
        response.setIncomeType(budget.getIncomeType());
        response.setIncomeSourceLabel(budget.getIncomeSourceLabel());
        response.setIncomeAmount(budget.getIncomeAmount().setScale(2));
        response.setAllocationMode(budget.getAllocationMode());
        response.setAllocationInputMode(budget.getAllocationInputMode() == null
                ? AllocationInputMode.PERCENTAGE
                : budget.getAllocationInputMode());
        response.setInvestmentsEnabled(budget.getInvestmentsEnabled());
        response.setEssentialsPercentage(budget.getEssentialsPercentage().setScale(2));
        response.setSafetyPercentage(budget.getSafetyPercentage().setScale(2));
        response.setInvestmentsPercentage(budget.getInvestmentsPercentage().setScale(2));
        BigDecimal e = budget.getEssentialsAmount() == null
                ? allocationCalculator.calculate(budget.getIncomeAmount(),
                new AllocationCalculator.Percentages(budget.getEssentialsPercentage(),
                        budget.getSafetyPercentage(), budget.getInvestmentsPercentage()),
                budget.getInvestmentsEnabled()).essentials()
                : budget.getEssentialsAmount();
        BigDecimal s = budget.getSafetyAmount() == null
                ? allocationCalculator.calculate(budget.getIncomeAmount(),
                new AllocationCalculator.Percentages(budget.getEssentialsPercentage(),
                        budget.getSafetyPercentage(), budget.getInvestmentsPercentage()),
                budget.getInvestmentsEnabled()).safety()
                : budget.getSafetyAmount();
        BigDecimal i = budget.getInvestmentsAmount() == null
                ? allocationCalculator.calculate(budget.getIncomeAmount(),
                new AllocationCalculator.Percentages(budget.getEssentialsPercentage(),
                        budget.getSafetyPercentage(), budget.getInvestmentsPercentage()),
                budget.getInvestmentsEnabled()).investments()
                : budget.getInvestmentsAmount();
        response.setEssentialsAmount(e.setScale(2));
        response.setSafetyAmount(s.setScale(2));
        response.setInvestmentsAmount(i.setScale(2));
        response.setCreatedAtUtc(budget.getCreatedAtUtc());
        response.setUpdatedAtUtc(budget.getUpdatedAtUtc());
        return response;
    }
}
