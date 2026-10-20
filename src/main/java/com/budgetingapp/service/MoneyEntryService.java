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
import com.budgetingapp.repository.MoneyEntrySpecifications;
import com.budgetingapp.repository.MonthlyBudgetRepository;
import com.budgetingapp.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class MoneyEntryService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999.99");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String CARD_SUMMARY_EXPLANATION =
            "Estimated credit-card outstanding (manual tracking): opening amount plus posted credit-card "
                    + "purchases minus posted credit-card payments since tracking start.";
    private static final String CARD_UNCONFIGURED_EXPLANATION =
            "Credit-card tracking is not configured; set an opening outstanding amount to start estimating.";

    private final MoneyEntryRepository moneyEntryRepository;
    private final MonthlyBudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public MoneyEntryService(MoneyEntryRepository moneyEntryRepository,
                             MonthlyBudgetRepository budgetRepository,
                             UserRepository userRepository,
                             PlatformTransactionManager transactionManager,
                             Clock clock) {
        this.moneyEntryRepository = moneyEntryRepository;
        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public MoneyEntryResponse createEntry(Long userId, MoneyEntryCreateRequest request) {
        requireCreateShape(request);
        String clientRequestId = normalizeClientRequestId(request.getClientRequestId());
        String note = normalizeNote(request.getNote());
        EntryType entryType = request.getEntryType();
        BudgetBucket bucket = request.getBucket();
        PaymentMethod paymentMethod = request.getPaymentMethod();
        String categoryCode = request.getCategoryCode();
        validateCombination(entryType, bucket, categoryCode, paymentMethod, note);
        BigDecimal amount = normalizeAmount(request.getAmount());
        Instant entryAtUtc = toEntryInstant(request.getEntryDate(), request.getEntryTime());
        YearMonth entryMonth = YearMonth.from(request.getEntryDate());
        if (entryMonth.isBefore(YearMonth.now(clock))) {
            throw new PeriodReadOnlyException(entryMonth.format(MONTH_FORMAT), "entry period");
        }
        if (entryAtUtc.isAfter(clock.instant())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ENTRY_DATE_FUTURE",
                    "entryDate and entryTime must not be in the future.");
        }

        try {
            return transactionTemplate.execute(txStatus -> {
                User user = lockUser(userId);
                if (clientRequestId != null) {
                    Optional<MoneyEntry> existing =
                            moneyEntryRepository.findByUser_UserIdAndClientRequestId(userId, clientRequestId);
                    if (existing.isPresent()) {
                        return idempotentResponse(existing.get(), entryAtUtc, entryType, bucket,
                                categoryCode, amount, paymentMethod, note);
                    }
                }
                assertDomainRules(user, entryMonth, entryType, paymentMethod, entryAtUtc, amount);
                MoneyEntry entry = new MoneyEntry();
                entry.setUser(user);
                entry.setEntryAtUtc(entryAtUtc);
                entry.setEntryType(entryType);
                entry.setBucket(bucket);
                entry.setCategoryCode(categoryCode);
                entry.setAmount(amount);
                entry.setPaymentMethod(paymentMethod);
                entry.setNote(note);
                entry.setStatus(EntryStatus.POSTED);
                entry.setClientRequestId(clientRequestId);
                MoneyEntry saved = moneyEntryRepository.saveAndFlush(entry);
                MoneyEntry fresh = moneyEntryRepository.findById(saved.getMoneyEntryId()).orElse(saved);
                return toResponse(fresh);
            });
        } catch (DataIntegrityViolationException ex) {
            return recoverCreate(userId, clientRequestId, entryAtUtc, entryType, bucket,
                    categoryCode, amount, paymentMethod, note);
        }
    }

    public MoneyEntryResponse voidEntry(Long userId, Long moneyEntryId, MoneyEntryVoidRequest request) {
        requireVoidShape(request);
        String voidReason = normalizeVoidReason(request.getVoidReason());
        return transactionTemplate.execute(txStatus -> {
            lockUser(userId);
            MoneyEntry entry = moneyEntryRepository.findByMoneyEntryIdAndUser_UserId(moneyEntryId, userId)
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ENTRY_NOT_FOUND",
                            "Money entry not found."));
            if (entry.getStatus() == EntryStatus.VOID) {
                throw new BusinessException(HttpStatus.CONFLICT, "ALREADY_VOIDED",
                        "The money entry is already void.");
            }
            entry.setStatus(EntryStatus.VOID);
            entry.setVoidedAtUtc(clock.instant());
            entry.setVoidReason(voidReason);
            MoneyEntry saved = moneyEntryRepository.saveAndFlush(entry);
            MoneyEntry fresh = moneyEntryRepository.findById(saved.getMoneyEntryId()).orElse(saved);
            return toResponse(fresh);
        });
    }

    @Transactional(readOnly = true)
    public MoneyEntryResponse getEntry(Long userId, Long moneyEntryId) {
        if (moneyEntryId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "moneyEntryId is required.");
        }
        return moneyEntryRepository.findByMoneyEntryIdAndUser_UserId(moneyEntryId, userId)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ENTRY_NOT_FOUND",
                        "Money entry not found."));
    }

    @Transactional(readOnly = true)
    public MoneyEntryPageResponse listEntries(Long userId, MoneyEntryListQuery query) {
        MoneyEntryListQuery effective = query != null ? query : new MoneyEntryListQuery();
        Instant startUtc = null;
        Instant endExclusiveUtc = null;
        if (effective.getFromDate() != null || effective.getToDate() != null) {
            if (effective.getFromDate() == null || effective.getToDate() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "DATE_RANGE_INCOMPLETE",
                        "fromDate and toDate must be provided together.");
            }
            if (effective.getToDate().isBefore(effective.getFromDate())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "DATE_RANGE_INVALID",
                        "toDate must not be before fromDate.");
            }
            UtcRange range = toUtcRange(effective.getFromDate(), effective.getToDate());
            startUtc = range.startUtc();
            endExclusiveUtc = range.endExclusiveUtc();
        }
        int page = effective.getPage() != null ? effective.getPage() : 0;
        int size = effective.getSize() != null ? effective.getSize() : DEFAULT_PAGE_SIZE;
        if (page < 0 || size < 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION",
                    "page must not be negative and size must be at least 1.");
        }
        if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }
        Specification<MoneyEntry> specification = MoneyEntrySpecifications.search(
                userId, startUtc, endExclusiveUtc, effective.getEntryType(), effective.getBucket(),
                effective.getCategoryCode(), effective.getStatus());
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("entryAtUtc"), Sort.Order.desc("moneyEntryId")));
        Page<MoneyEntry> result = moneyEntryRepository.findAll(specification, pageable);
        MoneyEntryPageResponse response = new MoneyEntryPageResponse();
        response.setContent(result.getContent().stream().map(this::toResponse).toList());
        response.setPage(result.getNumber());
        response.setSize(result.getSize());
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        return response;
    }

    @Transactional(readOnly = true)
    public CreditCardSummaryResponse getCreditCardSummary(Long userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                        "User not found."));
        if (user.getCreditCardTrackingStartedAtUtc() == null) {
            return buildSummary(user, null, null);
        }
        Instant trackingStart = user.getCreditCardTrackingStartedAtUtc();
        BigDecimal purchases = moneyEntryRepository
                .sumAmountByUser_UserIdAndEntryTypeAndPaymentMethodAndStatusAndEntryAtUtcGreaterThanEqual(
                        userId, EntryType.EXPENSE, PaymentMethod.CREDIT_CARD, EntryStatus.POSTED, trackingStart);
        BigDecimal payments = moneyEntryRepository
                .sumAmountByUser_UserIdAndEntryTypeAndStatusAndEntryAtUtcGreaterThanEqual(
                        userId, EntryType.CREDIT_CARD_PAYMENT, EntryStatus.POSTED, trackingStart);
        return buildSummary(user, purchases, payments);
    }

    public CreditCardSummaryResponse setCreditCardOpening(Long userId, CreditCardOpeningRequest request) {
        requireOpeningShape(request);
        BigDecimal opening = normalizeOpening(request.getOpeningOutstandingAmount());
        Instant requestedStart = request.getTrackingStartedAtUtc();
        return transactionTemplate.execute(txStatus -> {
            User user = lockUser(userId);
            Instant existingStart = user.getCreditCardTrackingStartedAtUtc();
            if (existingStart != null && hasPostedCardActivity(userId, existingStart)) {
                throw new BusinessException(HttpStatus.CONFLICT, "OPENING_BASELINE_LOCKED",
                        "Posted credit-card activity already exists; the opening baseline cannot be changed.");
            }
            user.setOpeningCreditCardOutstandingAmount(opening);
            user.setCreditCardTrackingStartedAtUtc(requestedStart != null ? requestedStart : clock.instant());
            userRepository.saveAndFlush(user);
            Instant trackingStart = user.getCreditCardTrackingStartedAtUtc();
            return buildSummary(user,
                    moneyEntryRepository.sumAmountByUser_UserIdAndEntryTypeAndPaymentMethodAndStatusAndEntryAtUtcGreaterThanEqual(
                            userId, EntryType.EXPENSE, PaymentMethod.CREDIT_CARD, EntryStatus.POSTED, trackingStart),
                    moneyEntryRepository.sumAmountByUser_UserIdAndEntryTypeAndStatusAndEntryAtUtcGreaterThanEqual(
                            userId, EntryType.CREDIT_CARD_PAYMENT, EntryStatus.POSTED, trackingStart));
        });
    }

    private MoneyEntryResponse recoverCreate(Long userId, String clientRequestId, Instant entryAtUtc,
                                             EntryType entryType, BudgetBucket bucket, String categoryCode,
                                             BigDecimal amount, PaymentMethod paymentMethod, String note) {
        if (clientRequestId == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "ENTRY_SAVE_FAILED",
                    "The money entry could not be saved.");
        }
        return transactionTemplate.execute(txStatus -> {
            Optional<MoneyEntry> existing =
                    moneyEntryRepository.findByUser_UserIdAndClientRequestId(userId, clientRequestId);
            if (existing.isEmpty()) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "ENTRY_SAVE_FAILED",
                        "The money entry could not be saved.");
            }
            return idempotentResponse(existing.get(), entryAtUtc, entryType, bucket,
                    categoryCode, amount, paymentMethod, note);
        });
    }

    private MoneyEntryResponse idempotentResponse(MoneyEntry existing, Instant entryAtUtc, EntryType entryType,
                                                  BudgetBucket bucket, String categoryCode, BigDecimal amount,
                                                  PaymentMethod paymentMethod, String note) {
        if (!samePayload(existing, entryAtUtc, entryType, bucket, categoryCode, amount, paymentMethod, note)) {
            throw new BusinessException(HttpStatus.CONFLICT, "CLIENT_REQUEST_ID_CONFLICT",
                    "clientRequestId was already used with a different payload.");
        }
        return toResponse(existing);
    }

    private boolean samePayload(MoneyEntry existing, Instant entryAtUtc, EntryType entryType, BudgetBucket bucket,
                                String categoryCode, BigDecimal amount, PaymentMethod paymentMethod, String note) {
        return existing.getEntryAtUtc().equals(entryAtUtc)
                && existing.getEntryType() == entryType
                && existing.getBucket() == bucket
                && Objects.equals(existing.getCategoryCode(), categoryCode)
                && existing.getAmount().compareTo(amount) == 0
                && existing.getPaymentMethod() == paymentMethod
                && Objects.equals(existing.getNote(), note);
    }

    private void assertDomainRules(User user, YearMonth entryMonth, EntryType entryType,
                                   PaymentMethod paymentMethod, Instant entryAtUtc, BigDecimal amount) {
        if (entryType == EntryType.INVESTMENT) {
            MonthlyBudget budget = budgetRepository
                    .findByUser_UserIdAndBudgetMonth(user.getUserId(), entryMonth.atDay(1))
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "BUDGET_NOT_SET",
                            "Set the budget for " + entryMonth.format(MONTH_FORMAT)
                                    + " before recording investments."));
            if (!Boolean.TRUE.equals(budget.getInvestmentsEnabled())) {
                throw new BusinessException(HttpStatus.CONFLICT, "INVESTMENTS_DISABLED",
                        "Investments are disabled in the budget for " + entryMonth.format(MONTH_FORMAT) + ".");
            }
        }
        boolean creditCardPurchase = entryType == EntryType.EXPENSE && paymentMethod == PaymentMethod.CREDIT_CARD;
        boolean creditCardPayment = entryType == EntryType.CREDIT_CARD_PAYMENT;
        if (!creditCardPurchase && !creditCardPayment) {
            return;
        }
        Instant trackingStart = user.getCreditCardTrackingStartedAtUtc();
        if (trackingStart == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "CREDIT_CARD_TRACKING_NOT_CONFIGURED",
                    "Configure credit-card tracking before recording credit-card activity.");
        }
        if (!creditCardPayment) {
            return;
        }
        if (entryAtUtc.isBefore(trackingStart)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PAYMENT_BEFORE_TRACKING_START",
                    "Credit-card payments must not be dated before tracking start.");
        }
        BigDecimal outstanding = outstandingFor(user.getUserId(), trackingStart,
                nullToZero(user.getOpeningCreditCardOutstandingAmount()));
        if (amount.compareTo(outstanding) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "PAYMENT_EXCEEDS_OUTSTANDING",
                    "amount exceeds the outstanding credit-card balance of " + outstanding.toPlainString() + ".");
        }
    }

    private BigDecimal outstandingFor(Long userId, Instant trackingStart, BigDecimal opening) {
        BigDecimal purchases = nullToZero(moneyEntryRepository
                .sumAmountByUser_UserIdAndEntryTypeAndPaymentMethodAndStatusAndEntryAtUtcGreaterThanEqual(
                        userId, EntryType.EXPENSE, PaymentMethod.CREDIT_CARD, EntryStatus.POSTED, trackingStart));
        BigDecimal payments = nullToZero(moneyEntryRepository
                .sumAmountByUser_UserIdAndEntryTypeAndStatusAndEntryAtUtcGreaterThanEqual(
                        userId, EntryType.CREDIT_CARD_PAYMENT, EntryStatus.POSTED, trackingStart));
        return opening.add(purchases).subtract(payments);
    }

    private boolean hasPostedCardActivity(Long userId, Instant trackingStart) {
        return moneyEntryRepository
                .existsByUser_UserIdAndEntryTypeAndPaymentMethodAndStatusAndEntryAtUtcGreaterThanEqual(
                        userId, EntryType.EXPENSE, PaymentMethod.CREDIT_CARD, EntryStatus.POSTED, trackingStart)
                || moneyEntryRepository
                .existsByUser_UserIdAndEntryTypeAndStatusAndEntryAtUtcGreaterThanEqual(
                        userId, EntryType.CREDIT_CARD_PAYMENT, EntryStatus.POSTED, trackingStart);
    }

    private CreditCardSummaryResponse buildSummary(User user, BigDecimal purchases, BigDecimal payments) {
        CreditCardSummaryResponse response = new CreditCardSummaryResponse();
        boolean configured = user.getCreditCardTrackingStartedAtUtc() != null;
        response.setTrackingConfigured(configured);
        if (!configured) {
            response.setOpeningOutstandingAmount(null);
            response.setCurrentOutstandingAmount(null);
            response.setTotalPostedPurchases(null);
            response.setTotalPostedPayments(null);
            response.setTrackingStartedAtUtc(null);
            response.setExplanation(CARD_UNCONFIGURED_EXPLANATION);
            return response;
        }
        BigDecimal opening = nullToZero(user.getOpeningCreditCardOutstandingAmount());
        BigDecimal totalPurchases = nullToZero(purchases);
        BigDecimal totalPayments = nullToZero(payments);
        response.setOpeningOutstandingAmount(opening.setScale(2));
        response.setTotalPostedPurchases(totalPurchases.setScale(2));
        response.setTotalPostedPayments(totalPayments.setScale(2));
        response.setCurrentOutstandingAmount(opening.add(totalPurchases).subtract(totalPayments).setScale(2));
        response.setTrackingStartedAtUtc(user.getCreditCardTrackingStartedAtUtc());
        response.setExplanation(CARD_SUMMARY_EXPLANATION);
        return response;
    }

    private void requireCreateShape(MoneyEntryCreateRequest request) {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "Request body is required.");
        }
        if (request.getEntryDate() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "entryDate is required.");
        }
        if (request.getEntryTime() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "entryTime is required.");
        }
        if (request.getEntryType() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "entryType is required.");
        }
        if (request.getCategoryCode() == null || request.getCategoryCode().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "categoryCode is required.");
        }
        if (request.getAmount() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "amount is required.");
        }
    }

    private void requireVoidShape(MoneyEntryVoidRequest request) {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "Request body is required.");
        }
    }

    private void requireOpeningShape(CreditCardOpeningRequest request) {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "Request body is required.");
        }
        if (request.getOpeningOutstandingAmount() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID",
                    "openingOutstandingAmount is required.");
        }
    }

    private void validateCombination(EntryType entryType, BudgetBucket bucket, String categoryCode,
                                     PaymentMethod paymentMethod, String note) {
        switch (entryType) {
            case EXPENSE -> {
                if (bucket != BudgetBucket.ESSENTIALS && bucket != BudgetBucket.SAFETY) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_BUCKET_FOR_ENTRY_TYPE",
                            "EXPENSE entries require bucket ESSENTIALS or SAFETY.");
                }
            }
            case SAFETY_SAVING -> {
                if (bucket != BudgetBucket.SAFETY) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_BUCKET_FOR_ENTRY_TYPE",
                            "SAFETY_SAVING entries require bucket SAFETY.");
                }
            }
            case INVESTMENT -> {
                if (bucket != BudgetBucket.INVESTMENTS) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_BUCKET_FOR_ENTRY_TYPE",
                            "INVESTMENT entries require bucket INVESTMENTS.");
                }
            }
            case CREDIT_CARD_PAYMENT -> {
                if (bucket != null) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_BUCKET_FOR_ENTRY_TYPE",
                            "CREDIT_CARD_PAYMENT entries must not have a bucket.");
                }
            }
        }
        boolean paymentRequired = entryType == EntryType.EXPENSE || entryType == EntryType.CREDIT_CARD_PAYMENT;
        if (paymentRequired && paymentMethod == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PAYMENT_METHOD_REQUIRED",
                    "paymentMethod is required for " + entryType + " entries.");
        }
        if (entryType != EntryType.EXPENSE && paymentMethod == PaymentMethod.CREDIT_CARD) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_METHOD_FOR_ENTRY_TYPE",
                    "paymentMethod CREDIT_CARD is only allowed for EXPENSE entries.");
        }
        if (!CategoryCatalog.isKnown(categoryCode)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CATEGORY_CODE_UNKNOWN",
                    "categoryCode " + categoryCode + " is not a known category.");
        }
        if (!CategoryCatalog.allowedCategories(entryType, bucket).contains(categoryCode)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY_FOR_BUCKET",
                    "categoryCode " + categoryCode + " is not allowed for " + entryType
                            + " entries with bucket " + bucket + ".");
        }
        if (note != null && note.length() > 500) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "NOTE_TOO_LONG",
                    "note must be at most 500 characters.");
        }
    }

    private String normalizeClientRequestId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.length() != 36) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CLIENT_REQUEST_ID_INVALID",
                    "clientRequestId must be a UUID.");
        }
        try {
            UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CLIENT_REQUEST_ID_INVALID",
                    "clientRequestId must be a UUID.");
        }
        return raw;
    }

    private String normalizeNote(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw;
    }

    private String normalizeVoidReason(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.length() > 250) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VOID_REASON_TOO_LONG",
                    "voidReason must be at most 250 characters.");
        }
        return raw;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "AMOUNT_INVALID",
                    "amount must be greater than 0.00 and at most 999999999999.99.");
        }
        if (amount.scale() > 2) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "AMOUNT_PRECISION",
                    "amount must have at most 2 decimal places.");
        }
        return amount.setScale(2);
    }

    private BigDecimal normalizeOpening(BigDecimal opening) {
        if (opening.signum() < 0 || opening.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "OPENING_AMOUNT_INVALID",
                    "openingOutstandingAmount must be between 0.00 and 999999999999.99.");
        }
        if (opening.scale() > 2) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "OPENING_AMOUNT_INVALID",
                    "openingOutstandingAmount must have at most 2 decimal places.");
        }
        return opening.setScale(2);
    }

    private User lockUser(Long userId) {
        return userRepository.findWithLockByUserId(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                        "User not found."));
    }

    private Instant toEntryInstant(LocalDate entryDate, LocalTime entryTime) {
        return LocalDateTime.of(entryDate, entryTime).atZone(BUSINESS_ZONE).toInstant();
    }

    private UtcRange toUtcRange(LocalDate fromDate, LocalDate toDate) {
        Instant startUtc = fromDate.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant endExclusiveUtc = toDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        return new UtcRange(startUtc, endExclusiveUtc);
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private MoneyEntryResponse toResponse(MoneyEntry entry) {
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
        response.setVoidedAtUtc(entry.getVoidedAtUtc());
        response.setVoidReason(entry.getVoidReason());
        response.setCreatedAtUtc(entry.getCreatedAtUtc());
        response.setUpdatedAtUtc(entry.getUpdatedAtUtc());
        return response;
    }

    record UtcRange(Instant startUtc, Instant endExclusiveUtc) {
    }
}
