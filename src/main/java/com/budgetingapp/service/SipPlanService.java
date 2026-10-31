package com.budgetingapp.service;

import com.budgetingapp.dto.request.SipInstallmentPaymentRequest;
import com.budgetingapp.dto.request.SipPlanCreateRequest;
import com.budgetingapp.dto.request.SipPlanStopRequest;
import com.budgetingapp.dto.request.SipPlanUpdateRequest;
import com.budgetingapp.dto.response.MoneyEntryResponse;
import com.budgetingapp.dto.response.SipCommitmentSummaryResponse;
import com.budgetingapp.dto.response.SipInstallmentListResponse;
import com.budgetingapp.dto.response.SipInstallmentPaymentResponse;
import com.budgetingapp.dto.response.SipInstallmentResponse;
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
import com.budgetingapp.exception.PeriodReadOnlyException;
import com.budgetingapp.repository.MoneyEntryRepository;
import com.budgetingapp.repository.MonthlyBudgetRepository;
import com.budgetingapp.repository.SipPlanRepository;
import com.budgetingapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SipPlanService {

    private static final Logger log = LoggerFactory.getLogger(SipPlanService.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999.99");
    private static final int MAX_RANGE_MONTHS = 60;

    private final SipPlanRepository sipPlanRepository;
    private final MoneyEntryRepository moneyEntryRepository;
    private final MonthlyBudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final SipScheduleCalculator scheduleCalculator;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public SipPlanService(SipPlanRepository sipPlanRepository,
                          MoneyEntryRepository moneyEntryRepository,
                          MonthlyBudgetRepository budgetRepository,
                          UserRepository userRepository,
                          SipScheduleCalculator scheduleCalculator,
                          PlatformTransactionManager transactionManager,
                          Clock clock) {
        this.sipPlanRepository = sipPlanRepository;
        this.moneyEntryRepository = moneyEntryRepository;
        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
        this.scheduleCalculator = scheduleCalculator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Transactional
    public SipPlanResponse createPlan(Long userId, SipPlanCreateRequest request) {
        requireCreateShape(request);
        String planName = normalizePlanName(request.getPlanName());
        BigDecimal monthlyAmount = normalizeMonthlyAmount(request.getMonthlyAmount());
        int installmentDay = validateInstallmentDay(request.getInstallmentDay());
        validateDates(request.getOriginalStartDate(), request.getTrackingStartDate(),
                request.getPlannedEndDate());
        return transactionTemplate.execute(txStatus -> {
            User user = lockUser(userId);
            SipPlan plan = new SipPlan();
            plan.setUser(user);
            plan.setPlanName(planName);
            plan.setMonthlyAmount(monthlyAmount);
            plan.setInstallmentDay(installmentDay);
            plan.setOriginalStartDate(request.getOriginalStartDate());
            plan.setTrackingStartDate(request.getTrackingStartDate());
            plan.setPlannedEndDate(request.getPlannedEndDate());
            plan.setStatus(SipPlanStatus.ACTIVE);
            SipPlan saved = sipPlanRepository.saveAndFlush(plan);
            SipPlan fresh = sipPlanRepository.findById(saved.getSipPlanId()).orElse(saved);
            log.info("investment_plan.created sipPlanId={} investmentType=SIP", fresh.getSipPlanId());
            return toPlanResponse(fresh);
        });
    }

    @Transactional(readOnly = true)
    public SipPlanListResponse listPlans(Long userId) {
        List<SipPlan> plans = sipPlanRepository
                .findByUser_UserIdOrderByCreatedAtUtcDescSipPlanIdDesc(userId);
        LocalDate today = today();
        BigDecimal commitment = plans.stream()
                .filter(plan -> scheduleCalculator.effectiveStatus(plan, today) == SipPlanStatus.ACTIVE)
                .map(SipPlan::getMonthlyAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
        SipPlanListResponse response = new SipPlanListResponse();
        response.setContent(plans.stream().map(plan -> toPlanResponse(plan, today)).toList());
        response.setActiveMonthlyCommitment(commitment);
        return response;
    }

    @Transactional(readOnly = true)
    public SipPlanResponse getPlan(Long userId, Long sipPlanId) {
        return toPlanResponse(findPlan(userId, sipPlanId));
    }

    @Transactional
    public SipPlanResponse updatePlan(Long userId, Long sipPlanId, SipPlanUpdateRequest request) {
        requireUpdateShape(request);
        String planName = normalizePlanName(request.getPlanName());
        BigDecimal monthlyAmount = normalizeMonthlyAmount(request.getMonthlyAmount());
        int installmentDay = validateInstallmentDay(request.getInstallmentDay());
        validateDates(request.getOriginalStartDate(), request.getTrackingStartDate(),
                request.getPlannedEndDate());
        return transactionTemplate.execute(txStatus -> {
            lockUser(userId);
            SipPlan plan = findPlan(userId, sipPlanId);
            SipPlanStatus effectiveStatus = scheduleCalculator.effectiveStatus(plan, today());
            if (effectiveStatus == SipPlanStatus.STOPPED) {
                throw new BusinessException(HttpStatus.CONFLICT, "SIP_PLAN_NOT_ACTIVE",
                        "Stopped SIP plans cannot be updated.");
            }
            if (effectiveStatus == SipPlanStatus.COMPLETED) {
                throw new BusinessException(HttpStatus.CONFLICT, "SIP_PLAN_COMPLETED",
                        "Completed SIP plans cannot be updated.");
            }
            boolean datesChanged = !plan.getOriginalStartDate().equals(request.getOriginalStartDate())
                    || !plan.getTrackingStartDate().equals(request.getTrackingStartDate());
            if (datesChanged && moneyEntryRepository.existsBySipPlan_SipPlanIdAndStatus(
                    sipPlanId, EntryStatus.POSTED)) {
                throw new BusinessException(HttpStatus.CONFLICT, "SIP_PLAN_DATES_LOCKED",
                        "Start dates cannot be changed after an installment has been paid.");
            }
            plan.setPlanName(planName);
            plan.setMonthlyAmount(monthlyAmount);
            plan.setInstallmentDay(installmentDay);
            plan.setOriginalStartDate(request.getOriginalStartDate());
            plan.setTrackingStartDate(request.getTrackingStartDate());
            plan.setPlannedEndDate(request.getPlannedEndDate());
            SipPlan saved = sipPlanRepository.saveAndFlush(plan);
            SipPlan fresh = sipPlanRepository.findById(saved.getSipPlanId()).orElse(saved);
            log.info("investment_plan.updated sipPlanId={} investmentType=SIP", fresh.getSipPlanId());
            return toPlanResponse(fresh);
        });
    }

    @Transactional
    public SipPlanResponse stopPlan(Long userId, Long sipPlanId, SipPlanStopRequest request) {
        LocalDate stopDate = request != null && request.getStopDate() != null
                ? request.getStopDate() : today();
        return transactionTemplate.execute(txStatus -> {
            lockUser(userId);
            SipPlan plan = findPlan(userId, sipPlanId);
            SipPlanStatus effectiveStatus = scheduleCalculator.effectiveStatus(plan, today());
            if (effectiveStatus == SipPlanStatus.STOPPED) {
                throw new BusinessException(HttpStatus.CONFLICT, "SIP_PLAN_ALREADY_STOPPED",
                        "The SIP plan is already stopped.");
            }
            if (effectiveStatus == SipPlanStatus.COMPLETED) {
                throw new BusinessException(HttpStatus.CONFLICT, "SIP_PLAN_COMPLETED",
                        "Completed SIP plans cannot be stopped.");
            }
            validateStopDate(plan, stopDate);
            plan.setActualEndedDate(stopDate);
            plan.setStatus(SipPlanStatus.STOPPED);
            SipPlan saved = sipPlanRepository.saveAndFlush(plan);
            SipPlan fresh = sipPlanRepository.findById(saved.getSipPlanId()).orElse(saved);
            log.info("investment_plan.stopped sipPlanId={} investmentType=SIP", fresh.getSipPlanId());
            return toPlanResponse(fresh);
        });
    }

    @Transactional(readOnly = true)
    public SipInstallmentListResponse listInstallments(Long userId, Long sipPlanId,
                                                       LocalDate fromDate, LocalDate toDate) {
        DateRange range = resolveRange(fromDate, toDate);
        List<SipPlan> plans;
        if (sipPlanId == null) {
            plans = sipPlanRepository.findByUser_UserIdOrderByCreatedAtUtcDescSipPlanIdDesc(userId);
        } else {
            plans = List.of(findPlan(userId, sipPlanId));
        }
        LocalDate startPeriod = YearMonth.from(range.fromDate()).atDay(1);
        LocalDate endPeriod = YearMonth.from(range.toDate()).atDay(1);
        List<MoneyEntry> paidEntries = sipPlanId == null
                ? moneyEntryRepository.findByUser_UserIdAndSipPlanIsNotNullAndStatusAndSipInstallmentPeriodBetween(
                userId, EntryStatus.POSTED, startPeriod, endPeriod)
                : moneyEntryRepository.findByUser_UserIdAndSipPlan_SipPlanIdAndStatusAndSipInstallmentPeriodBetween(
                userId, sipPlanId, EntryStatus.POSTED, startPeriod, endPeriod);
        Map<String, MoneyEntry> paidByPeriod = new HashMap<>();
        for (MoneyEntry entry : paidEntries) {
            paidByPeriod.put(entry.getSipPlan().getSipPlanId() + ":" + entry.getSipInstallmentPeriod(), entry);
        }
        LocalDate today = today();
        List<SipInstallmentResponse> content = new ArrayList<>();
        for (SipPlan plan : plans) {
            for (LocalDate dueDate : scheduleCalculator.scheduledDates(
                    plan, range.fromDate(), range.toDate())) {
                YearMonth period = YearMonth.from(dueDate);
                MoneyEntry paidEntry = paidByPeriod.get(plan.getSipPlanId() + ":" + period.atDay(1));
                content.add(toInstallmentResponse(plan, period, dueDate, paidEntry, today));
            }
        }
        content.sort(Comparator.comparing(SipInstallmentResponse::getDueDate)
                .thenComparing(SipInstallmentResponse::getSipPlanId));
        SipInstallmentListResponse response = new SipInstallmentListResponse();
        response.setFromDate(range.fromDate());
        response.setToDate(range.toDate());
        response.setContent(List.copyOf(content));
        return response;
    }

    public SipInstallmentPaymentResponse markPaid(Long userId, Long sipPlanId, String periodText,
                                                  SipInstallmentPaymentRequest request) {
        YearMonth period = parsePeriod(periodText);
        SipInstallmentPaymentRequest effective = request != null
                ? request : new SipInstallmentPaymentRequest();
        String note = normalizeNote(effective.getNote());
        LocalDate paymentDate = effective.getPaymentDate() != null
                ? effective.getPaymentDate() : today();
        LocalTime paymentTime = effective.getPaymentTime() != null
                ? effective.getPaymentTime() : LocalTime.now(clock);
        Instant entryAtUtc = LocalDateTime.of(paymentDate, paymentTime)
                .atZone(BUSINESS_ZONE).toInstant();
        YearMonth entryMonth = YearMonth.from(paymentDate);
        if (entryMonth.isBefore(YearMonth.now(clock))) {
            throw new PeriodReadOnlyException(entryMonth.format(MONTH_FORMAT), "entry period");
        }
        if (entryAtUtc.isAfter(clock.instant())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ENTRY_DATE_FUTURE",
                    "paymentDate and paymentTime must not be in the future.");
        }
        LocalDate periodDate = period.atDay(1);
        try {
            return transactionTemplate.execute(txStatus -> {
                User user = lockUser(userId);
                SipPlan plan = findPlan(userId, sipPlanId);
                Optional<MoneyEntry> existing = moneyEntryRepository
                        .findByUser_UserIdAndSipPlan_SipPlanIdAndSipInstallmentPeriodAndStatus(
                                userId, sipPlanId, periodDate, EntryStatus.POSTED);
                if (existing.isPresent()) {
                    log.info("sip_installment.idempotent_replay sipPlanId={} period={} moneyEntryId={}",
                            sipPlanId, period.format(MONTH_FORMAT), existing.get().getMoneyEntryId());
                    return toPaymentResponse(plan, period, existing.get(), true);
                }
                SipPlanStatus effectiveStatus = scheduleCalculator.effectiveStatus(plan, today());
                if (effectiveStatus == SipPlanStatus.STOPPED) {
                    throw new BusinessException(HttpStatus.CONFLICT, "SIP_PLAN_NOT_ACTIVE",
                            "Stopped SIP plans cannot be paid.");
                }
                if (effectiveStatus == SipPlanStatus.COMPLETED) {
                    throw new BusinessException(HttpStatus.CONFLICT, "SIP_PLAN_COMPLETED",
                            "Completed SIP plans cannot be paid.");
                }
                if (!scheduleCalculator.isScheduled(plan, period)) {
                    throw new BusinessException(HttpStatus.CONFLICT, "SIP_INSTALLMENT_NOT_SCHEDULED",
                            "The requested installment period is not part of this SIP plan schedule.");
                }
                assertInvestmentBudget(user, entryMonth);
                MoneyEntry entry = new MoneyEntry();
                entry.setUser(user);
                entry.setEntryAtUtc(entryAtUtc);
                entry.setEntryType(EntryType.INVESTMENT);
                entry.setBucket(BudgetBucket.INVESTMENTS);
                entry.setCategoryCode(CategoryCatalog.SIP);
                entry.setAmount(plan.getMonthlyAmount().setScale(2));
                entry.setNote(note);
                entry.setStatus(EntryStatus.POSTED);
                entry.setSipPlan(plan);
                entry.setSipInstallmentPeriod(periodDate);
                MoneyEntry saved = moneyEntryRepository.saveAndFlush(entry);
                MoneyEntry fresh = moneyEntryRepository.findById(saved.getMoneyEntryId()).orElse(saved);
                log.info("sip_installment.marked_paid sipPlanId={} period={} moneyEntryId={}",
                        sipPlanId, period.format(MONTH_FORMAT), fresh.getMoneyEntryId());
                return toPaymentResponse(plan, period, fresh, false);
            });
        } catch (DataIntegrityViolationException ex) {
            return recoverPayment(userId, sipPlanId, period, periodDate);
        }
    }

    @Transactional(readOnly = true)
    public SipCommitmentSummaryResponse getCommitmentSummary(Long userId, String monthText) {
        YearMonth month = parseMonth(monthText);
        LocalDate fromDate = month.atDay(1);
        LocalDate toDate = month.atEndOfMonth();
        List<SipPlan> plans = sipPlanRepository
                .findByUser_UserIdOrderByCreatedAtUtcDescSipPlanIdDesc(userId);
        LocalDate today = today();
        BigDecimal commitment = BigDecimal.ZERO;
        int activePlanCount = 0;
        for (SipPlan plan : plans) {
            if (scheduleCalculator.effectiveStatus(plan, today) != SipPlanStatus.ACTIVE) {
                continue;
            }
            if (!scheduleCalculator.scheduledDates(plan, fromDate, toDate).isEmpty()) {
                commitment = commitment.add(plan.getMonthlyAmount());
                activePlanCount++;
            }
        }
        commitment = commitment.setScale(2);
        MonthlyBudget budget = budgetRepository
                .findByUser_UserIdAndBudgetMonth(userId, fromDate)
                .orElse(null);
        BigDecimal investmentsAmount = budget != null
                ? budget.getInvestmentsAmount().setScale(2) : null;
        SipCommitmentSummaryResponse response = new SipCommitmentSummaryResponse();
        response.setMonth(month.format(MONTH_FORMAT));
        response.setInvestmentsAmount(investmentsAmount);
        response.setActiveMonthlyCommitment(commitment);
        response.setActivePlanCount(activePlanCount);
        if (investmentsAmount == null) {
            response.setWithinAllocation(null);
            response.setOverAllocationAmount(BigDecimal.ZERO.setScale(2));
            response.setMessage("No budget is set for this month; SIP commitment cannot be compared yet.");
        } else if (commitment.compareTo(investmentsAmount) > 0) {
            response.setWithinAllocation(Boolean.FALSE);
            response.setOverAllocationAmount(commitment.subtract(investmentsAmount).setScale(2));
            response.setMessage("Active SIP commitments exceed this month's Investments allocation.");
        } else {
            response.setWithinAllocation(Boolean.TRUE);
            response.setOverAllocationAmount(BigDecimal.ZERO.setScale(2));
            response.setMessage("Active SIP commitments are within this month's Investments allocation.");
        }
        return response;
    }

    private SipInstallmentPaymentResponse recoverPayment(Long userId, Long sipPlanId,
                                                         YearMonth period, LocalDate periodDate) {
        return transactionTemplate.execute(txStatus -> {
            SipPlan plan = findPlan(userId, sipPlanId);
            Optional<MoneyEntry> existing = moneyEntryRepository
                    .findByUser_UserIdAndSipPlan_SipPlanIdAndSipInstallmentPeriodAndStatus(
                            userId, sipPlanId, periodDate, EntryStatus.POSTED);
            if (existing.isEmpty()) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "SIP_INSTALLMENT_SAVE_FAILED",
                        "The SIP installment could not be saved.");
            }
            log.info("sip_installment.idempotent_replay sipPlanId={} period={} moneyEntryId={}",
                    sipPlanId, period.format(MONTH_FORMAT), existing.get().getMoneyEntryId());
            return toPaymentResponse(plan, period, existing.get(), true);
        });
    }

    private SipInstallmentPaymentResponse toPaymentResponse(SipPlan plan, YearMonth period,
                                                            MoneyEntry entry, boolean replay) {
        SipInstallmentResponse installment = toInstallmentResponse(
                plan, period, scheduleCalculator.scheduledDate(period, plan.getInstallmentDay()),
                entry, today());
        SipInstallmentPaymentResponse response = new SipInstallmentPaymentResponse();
        response.setInstallment(installment);
        response.setMoneyEntry(toMoneyEntryResponse(entry));
        response.setIdempotentReplay(replay);
        return response;
    }

    private SipInstallmentResponse toInstallmentResponse(SipPlan plan, YearMonth period,
                                                         LocalDate dueDate, MoneyEntry paidEntry,
                                                         LocalDate today) {
        SipInstallmentResponse response = new SipInstallmentResponse();
        response.setSipPlanId(plan.getSipPlanId());
        response.setPlanName(plan.getPlanName());
        response.setPeriod(period.format(MONTH_FORMAT));
        response.setDueDate(dueDate);
        if (paidEntry != null) {
            response.setAmount(paidEntry.getAmount().setScale(2));
            response.setStatus(SipInstallmentStatus.PAID);
            response.setPaidMoneyEntryId(paidEntry.getMoneyEntryId());
            response.setPaidAtUtc(paidEntry.getEntryAtUtc());
            response.setPaidDate(paidEntry.getEntryAtUtc().atZone(BUSINESS_ZONE).toLocalDate());
        } else {
            response.setAmount(plan.getMonthlyAmount().setScale(2));
            response.setStatus(scheduleCalculator.installmentStatus(dueDate, false, today));
        }
        return response;
    }

    private MoneyEntryResponse toMoneyEntryResponse(MoneyEntry entry) {
        MoneyEntryResponse response = new MoneyEntryResponse();
        response.setMoneyEntryId(entry.getMoneyEntryId());
        response.setEntryAtUtc(entry.getEntryAtUtc());
        response.setEntryType(entry.getEntryType());
        response.setBucket(entry.getBucket());
        response.setCategoryCode(entry.getCategoryCode());
        response.setAmount(entry.getAmount().setScale(2));
        response.setPaymentMethod(entry.getPaymentMethod());
        response.setNote(entry.getNote());
        response.setStatus(entry.getStatus());
        response.setClientRequestId(entry.getClientRequestId());
        response.setSipPlanId(entry.getSipPlan() != null ? entry.getSipPlan().getSipPlanId() : null);
        response.setSipInstallmentPeriod(entry.getSipInstallmentPeriod());
        response.setVoidedAtUtc(entry.getVoidedAtUtc());
        response.setVoidReason(entry.getVoidReason());
        response.setCreatedAtUtc(entry.getCreatedAtUtc());
        response.setUpdatedAtUtc(entry.getUpdatedAtUtc());
        return response;
    }

    private SipPlanResponse toPlanResponse(SipPlan plan) {
        return toPlanResponse(plan, today());
    }

    private SipPlanResponse toPlanResponse(SipPlan plan, LocalDate today) {
        SipPlanResponse response = new SipPlanResponse();
        response.setSipPlanId(plan.getSipPlanId());
        response.setInvestmentType("SIP");
        response.setPlanName(plan.getPlanName());
        response.setMonthlyAmount(plan.getMonthlyAmount().setScale(2));
        response.setInstallmentDay(plan.getInstallmentDay());
        response.setOriginalStartDate(plan.getOriginalStartDate());
        response.setTrackingStartDate(plan.getTrackingStartDate());
        response.setPlannedEndDate(plan.getPlannedEndDate());
        response.setActualEndedDate(plan.getActualEndedDate());
        response.setStatus(scheduleCalculator.effectiveStatus(plan, today));
        response.setCreatedAtUtc(plan.getCreatedAtUtc());
        response.setUpdatedAtUtc(plan.getUpdatedAtUtc());
        return response;
    }

    private SipPlan findPlan(Long userId, Long sipPlanId) {
        if (sipPlanId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "sipPlanId is required.");
        }
        return sipPlanRepository.findBySipPlanIdAndUser_UserId(sipPlanId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "SIP_PLAN_NOT_FOUND",
                        "SIP plan not found."));
    }

    private void assertInvestmentBudget(User user, YearMonth entryMonth) {
        MonthlyBudget budget = budgetRepository
                .findByUser_UserIdAndBudgetMonth(user.getUserId(), entryMonth.atDay(1))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "BUDGET_NOT_SET",
                        "Set the budget for " + entryMonth.format(MONTH_FORMAT)
                                + " before recording investments."));
        if (!Boolean.TRUE.equals(budget.getInvestmentsEnabled())) {
            throw new BusinessException(HttpStatus.CONFLICT, "INVESTMENTS_DISABLED",
                    "Investments are disabled in the budget for " + entryMonth.format(MONTH_FORMAT)
                            + ". Turn Investments on in the budget to record investment entries.");
        }
    }

    private User lockUser(Long userId) {
        return userRepository.findWithLockByUserId(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                        "User not found."));
    }

    private void requireCreateShape(SipPlanCreateRequest request) {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "Request body is required.");
        }
        if (request.getOriginalStartDate() == null || request.getTrackingStartDate() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID",
                    "originalStartDate and trackingStartDate are required.");
        }
    }

    private void requireUpdateShape(SipPlanUpdateRequest request) {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "Request body is required.");
        }
        if (request.getOriginalStartDate() == null || request.getTrackingStartDate() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID",
                    "originalStartDate and trackingStartDate are required.");
        }
    }

    private String normalizePlanName(String raw) {
        if (raw == null || raw.isBlank() || raw.trim().length() > 120) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PLAN_NAME_INVALID",
                    "planName must be nonblank and at most 120 characters.");
        }
        return raw.trim();
    }

    private BigDecimal normalizeMonthlyAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MONTHLY_AMOUNT_INVALID",
                    "monthlyAmount must be greater than 0.00 and at most 999999999999.99.");
        }
        if (amount.scale() > 2) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MONTHLY_AMOUNT_PRECISION",
                    "monthlyAmount must have at most 2 decimal places.");
        }
        return amount.setScale(2);
    }

    private int validateInstallmentDay(Integer installmentDay) {
        if (installmentDay == null || installmentDay < 1 || installmentDay > 31) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INSTALLMENT_DAY_INVALID",
                    "installmentDay must be between 1 and 31.");
        }
        return installmentDay;
    }

    private void validateDates(LocalDate originalStartDate, LocalDate trackingStartDate,
                               LocalDate plannedEndDate) {
        if (trackingStartDate.isBefore(originalStartDate)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "TRACKING_START_BEFORE_ORIGINAL",
                    "trackingStartDate must not be before originalStartDate.");
        }
        if (plannedEndDate != null && plannedEndDate.isBefore(trackingStartDate)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PLANNED_END_BEFORE_TRACKING",
                    "plannedEndDate must not be before trackingStartDate.");
        }
    }

    private void validateStopDate(SipPlan plan, LocalDate stopDate) {
        LocalDate today = today();
        if (stopDate.isAfter(today)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SIP_STOP_DATE_FUTURE",
                    "stopDate must not be in the future.");
        }
        if (stopDate.isBefore(plan.getTrackingStartDate())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SIP_STOP_DATE_BEFORE_TRACKING",
                    "stopDate must not be before trackingStartDate.");
        }
        if (plan.getPlannedEndDate() != null && stopDate.isAfter(plan.getPlannedEndDate())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SIP_STOP_DATE_INVALID",
                    "stopDate must not be after plannedEndDate.");
        }
    }

    private String normalizeNote(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.length() > 500) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "NOTE_TOO_LONG",
                    "note must be at most 500 characters.");
        }
        return raw;
    }

    private YearMonth parsePeriod(String periodText) {
        if (periodText == null || periodText.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INSTALLMENT_PERIOD_INVALID",
                    "Installment period must use YYYY-MM format.");
        }
        try {
            return YearMonth.parse(periodText, MONTH_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INSTALLMENT_PERIOD_INVALID",
                    "Installment period must use YYYY-MM format.");
        }
    }

    private YearMonth parseMonth(String monthText) {
        if (monthText == null || monthText.isBlank()) {
            return YearMonth.now(clock);
        }
        try {
            return YearMonth.parse(monthText, MONTH_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MONTH_INVALID",
                    "month must use YYYY-MM format.");
        }
    }

    private DateRange resolveRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null && toDate == null) {
            YearMonth current = YearMonth.now(clock);
            return new DateRange(current.atDay(1), current.atEndOfMonth());
        }
        if (fromDate == null || toDate == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DATE_RANGE_INCOMPLETE",
                    "fromDate and toDate must be provided together.");
        }
        if (toDate.isBefore(fromDate)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DATE_RANGE_INVALID",
                    "toDate must not be before fromDate.");
        }
        long months = ChronoUnit.MONTHS.between(YearMonth.from(fromDate), YearMonth.from(toDate));
        if (months >= MAX_RANGE_MONTHS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DATE_RANGE_TOO_LARGE",
                    "Installment range must not exceed 60 months.");
        }
        return new DateRange(fromDate, toDate);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private record DateRange(LocalDate fromDate, LocalDate toDate) {
    }
}
