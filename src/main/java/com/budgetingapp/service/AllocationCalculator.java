package com.budgetingapp.service;

import com.budgetingapp.entity.enums.AllocationMode;
import com.budgetingapp.exception.AllocationValidationException;
import com.budgetingapp.exception.BusinessException;
import com.budgetingapp.exception.BusinessException.FieldViolation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class AllocationCalculator {

    public record Percentages(BigDecimal essentials, BigDecimal safety, BigDecimal investments) {
    }

    public record Amounts(BigDecimal essentials, BigDecimal safety, BigDecimal investments) {
    }

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MIN_PERCENTAGE = new BigDecimal("0.00");
    private static final BigDecimal MAX_PERCENTAGE = new BigDecimal("100.00");

    private static final Map<AllocationMode, Percentages> PRESETS = new EnumMap<>(AllocationMode.class);

    static {
        PRESETS.put(AllocationMode.CONSERVATIVE,
                new Percentages(new BigDecimal("60.00"), new BigDecimal("10.00"), new BigDecimal("30.00")));
        PRESETS.put(AllocationMode.BALANCED,
                new Percentages(new BigDecimal("55.00"), new BigDecimal("10.00"), new BigDecimal("35.00")));
        PRESETS.put(AllocationMode.AGGRESSIVE,
                new Percentages(new BigDecimal("50.00"), new BigDecimal("10.00"), new BigDecimal("40.00")));
    }

    public Percentages resolve(AllocationMode mode, boolean investmentsEnabled,
                               BigDecimal essentials, BigDecimal safety, BigDecimal investments) {
        if (mode == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "allocationMode is required.");
        }
        if (!investmentsEnabled) {
            return resolveInvestmentsOff(mode, essentials, safety, investments);
        }
        if (mode != AllocationMode.MANUAL) {
            Percentages preset = PRESETS.get(mode);
            validatePresetMatch(mode, preset, essentials, safety, investments);
            return preset;
        }
        return resolveManual(essentials, safety, investments,
                "Essentials, Safety and Investments must total exactly 100.00 when Investments are enabled.");
    }

    public Amounts calculate(BigDecimal income, Percentages percentages, boolean investmentsEnabled) {
        BigDecimal base = income.setScale(2, RoundingMode.HALF_UP);
        if (!investmentsEnabled) {
            BigDecimal essentials = bucketAmount(base, percentages.essentials(), RoundingMode.HALF_UP);
            return new Amounts(essentials, base.subtract(essentials), BigDecimal.ZERO.setScale(2));
        }
        BigDecimal essentials = bucketAmount(base, percentages.essentials(), RoundingMode.HALF_UP);
        BigDecimal safety = bucketAmount(base, percentages.safety(), RoundingMode.HALF_UP);
        BigDecimal investmentsAmount = base.subtract(essentials).subtract(safety);
        if (investmentsAmount.signum() < 0) {
            essentials = bucketAmount(base, percentages.essentials(), RoundingMode.DOWN);
            safety = bucketAmount(base, percentages.safety(), RoundingMode.DOWN);
            investmentsAmount = base.subtract(essentials).subtract(safety);
        }
        return new Amounts(essentials, safety, investmentsAmount);
    }

    private Percentages resolveInvestmentsOff(AllocationMode mode, BigDecimal essentials,
                                              BigDecimal safety, BigDecimal investments) {
        if (mode != AllocationMode.MANUAL) {
            throw AllocationValidationException.of("INVESTMENTS_OFF_REQUIRES_MANUAL",
                    "Choose Manual allocation and set Essentials and Safety so they total exactly 100.00 "
                            + "when Investments are disabled.",
                    "allocationMode", "INVESTMENTS_OFF_REQUIRES_MANUAL");
        }
        if (investments != null && investments.signum() != 0) {
            throw AllocationValidationException.of("INVESTMENTS_MUST_BE_ZERO",
                    "investmentsPercentage must be 0.00 when Investments are disabled.",
                    "investmentsPercentage", "INVESTMENTS_MUST_BE_ZERO");
        }
        return resolveManual(essentials, safety, BigDecimal.ZERO.setScale(2),
                "Essentials and Safety must total 100.00 when Investments are disabled.");
    }

    private Percentages resolveManual(BigDecimal essentials, BigDecimal safety, BigDecimal investments,
                                      String totalMessage) {
        List<FieldViolation> violations = new ArrayList<>();
        validatePercentage("essentialsPercentage", essentials, violations);
        validatePercentage("safetyPercentage", safety, violations);
        validatePercentage("investmentsPercentage", investments, violations);
        if (!violations.isEmpty()) {
            throw new AllocationValidationException("ALLOCATION_PERCENTAGE_INVALID",
                    "Each percentage must be between 0.00 and 100.00 with at most 2 decimal places.",
                    violations);
        }
        if (essentials.add(safety).add(investments).compareTo(HUNDRED) != 0) {
            String fieldCode = "TOTAL_MUST_EQUAL_100";
            throw new AllocationValidationException("ALLOCATION_TOTAL_INVALID", totalMessage,
                    List.of(new FieldViolation("essentialsPercentage", fieldCode, totalMessage),
                            new FieldViolation("safetyPercentage", fieldCode, totalMessage),
                            new FieldViolation("investmentsPercentage", fieldCode, totalMessage)));
        }
        return new Percentages(essentials.setScale(2), safety.setScale(2), investments.setScale(2));
    }

    private void validatePresetMatch(AllocationMode mode, Percentages preset,
                                     BigDecimal essentials, BigDecimal safety, BigDecimal investments) {
        List<FieldViolation> violations = new ArrayList<>();
        collectMismatch("essentialsPercentage", preset.essentials(), essentials, violations);
        collectMismatch("safetyPercentage", preset.safety(), safety, violations);
        collectMismatch("investmentsPercentage", preset.investments(), investments, violations);
        if (!violations.isEmpty()) {
            throw new AllocationValidationException("ALLOCATION_PRESET_MISMATCH",
                    mode + " preset requires the exact percentages " + preset.essentials() + "/"
                            + preset.safety() + "/" + preset.investments()
                            + ". Clear custom percentages or choose MANUAL mode.",
                    violations);
        }
    }

    private void collectMismatch(String field, BigDecimal expected, BigDecimal provided,
                                 List<FieldViolation> violations) {
        if (provided != null && provided.compareTo(expected) != 0) {
            violations.add(new FieldViolation(field, "PRESET_PERCENTAGE_MISMATCH",
                    "Preset value " + expected.toPlainString() + " cannot be overridden."));
        }
    }

    private void validatePercentage(String field, BigDecimal value, List<FieldViolation> violations) {
        if (value == null) {
            violations.add(new FieldViolation(field, "REQUIRED", field + " is required."));
            return;
        }
        if (value.scale() > 2) {
            violations.add(new FieldViolation(field, "MAX_TWO_DECIMALS",
                    field + " must have at most 2 decimal places."));
            return;
        }
        if (value.compareTo(MIN_PERCENTAGE) < 0 || value.compareTo(MAX_PERCENTAGE) > 0) {
            violations.add(new FieldViolation(field, "OUT_OF_RANGE",
                    field + " must be between 0.00 and 100.00."));
        }
    }

    public Amounts calculateAmounts(BigDecimal income, BigDecimal essentialsAmount,
                                    BigDecimal safetyAmount, BigDecimal investmentsAmount) {
        if (income == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "incomeAmount is required.");
        }
        BigDecimal normalizedIncome = income.setScale(2, RoundingMode.HALF_UP);
        if (essentialsAmount == null || safetyAmount == null || investmentsAmount == null) {
            throw new AllocationValidationException("ALLOCATION_AMOUNT_INVALID",
                    "essentialsAmount, safetyAmount and investmentsAmount are required for amount mode.",
                    List.of(new FieldViolation("essentialsAmount", "REQUIRED",
                                    "essentialsAmount, safetyAmount and investmentsAmount are required for amount mode."),
                            new FieldViolation("safetyAmount", "REQUIRED",
                                    "essentialsAmount, safetyAmount and investmentsAmount are required for amount mode."),
                            new FieldViolation("investmentsAmount", "REQUIRED",
                                    "essentialsAmount, safetyAmount and investmentsAmount are required for amount mode.")));
        }
        List<FieldViolation> violations = new ArrayList<>();
        validateAmount("essentialsAmount", essentialsAmount, violations);
        validateAmount("safetyAmount", safetyAmount, violations);
        validateAmount("investmentsAmount", investmentsAmount, violations);
        if (!violations.isEmpty()) {
            throw new AllocationValidationException("ALLOCATION_AMOUNT_INVALID",
                    "Each amount must be non-negative and have at most 2 decimal places.", violations);
        }
        BigDecimal e = essentialsAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal s = safetyAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal i = investmentsAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal sum = e.add(s).add(i);
        if (sum.compareTo(normalizedIncome) != 0) {
            String message = "Bucket amounts must total exactly the income amount.";
            throw new AllocationValidationException("ALLOCATION_AMOUNT_TOTAL_MISMATCH", message,
                    List.of(new FieldViolation("essentialsAmount", "TOTAL_MISMATCH", message),
                            new FieldViolation("safetyAmount", "TOTAL_MISMATCH", message),
                            new FieldViolation("investmentsAmount", "TOTAL_MISMATCH", message)));
        }
        return new Amounts(e, s, i);
    }

    public Percentages percentagesFromAmounts(BigDecimal income, BigDecimal essentialsAmount,
                                               BigDecimal safetyAmount, BigDecimal investmentsAmount) {
        if (income == null || income.compareTo(BigDecimal.ZERO) == 0) {
            return new Percentages(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(2));
        }
        BigDecimal base = income.setScale(2, RoundingMode.HALF_UP);
        BigDecimal e = essentialsAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal s = safetyAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal i = investmentsAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal ep = e.multiply(HUNDRED).divide(base, 2, RoundingMode.HALF_UP);
        BigDecimal sp = s.multiply(HUNDRED).divide(base, 2, RoundingMode.HALF_UP);
        BigDecimal ip = i.multiply(HUNDRED).divide(base, 2, RoundingMode.HALF_UP);
        return new Percentages(ep, sp, ip);
    }

    private void validateAmount(String field, BigDecimal value, List<FieldViolation> violations) {
        if (value.scale() > 2) {
            violations.add(new FieldViolation(field, "MAX_TWO_DECIMALS",
                    field + " must have at most 2 decimal places."));
            return;
        }
        if (value.compareTo(MIN_PERCENTAGE) < 0) {
            violations.add(new FieldViolation(field, "OUT_OF_RANGE",
                    field + " must not be negative."));
        }
    }

    private BigDecimal bucketAmount(BigDecimal income, BigDecimal percentage, RoundingMode mode) {
        return income.multiply(percentage).divide(HUNDRED, 2, mode);
    }
}
