package com.budgetingapp.service;

import com.budgetingapp.entity.enums.AllocationMode;
import com.budgetingapp.exception.AllocationValidationException;
import com.budgetingapp.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationCalculatorTest {

    private final AllocationCalculator calculator = new AllocationCalculator();

    @ParameterizedTest
    @EnumSource(value = AllocationMode.class, names = {"CONSERVATIVE", "BALANCED", "AGGRESSIVE"})
    void presetModesResolveToExactPresetPercentages(AllocationMode mode) {
        AllocationCalculator.Percentages result = calculator.resolve(mode, true, null, null, null);
        AllocationCalculator.Percentages expected = switch (mode) {
            case CONSERVATIVE -> new AllocationCalculator.Percentages(
                    new BigDecimal("60.00"), new BigDecimal("10.00"), new BigDecimal("30.00"));
            case BALANCED -> new AllocationCalculator.Percentages(
                    new BigDecimal("55.00"), new BigDecimal("10.00"), new BigDecimal("35.00"));
            case AGGRESSIVE -> new AllocationCalculator.Percentages(
                    new BigDecimal("50.00"), new BigDecimal("10.00"), new BigDecimal("40.00"));
            case MANUAL -> throw new IllegalStateException();
        };
        assertEquals(0, expected.essentials().compareTo(result.essentials()));
        assertEquals(0, expected.safety().compareTo(result.safety()));
        assertEquals(0, expected.investments().compareTo(result.investments()));
        assertEquals(2, result.essentials().scale());
        assertEquals(2, result.safety().scale());
        assertEquals(2, result.investments().scale());
    }

    @Test
    void presetWithMatchingClientPercentagesIsAccepted() {
        AllocationCalculator.Percentages result = calculator.resolve(
                AllocationMode.BALANCED, true, new BigDecimal("55.00"), new BigDecimal("10"), new BigDecimal("35"));
        assertEquals(0, new BigDecimal("55.00").compareTo(result.essentials()));
        assertEquals(0, new BigDecimal("10.00").compareTo(result.safety()));
        assertEquals(0, new BigDecimal("35.00").compareTo(result.investments()));
    }

    @Test
    void presetWithMismatchingClientPercentagesIsRejected() {
        BusinessException ex = assertThrows(AllocationValidationException.class, () ->
                calculator.resolve(AllocationMode.AGGRESSIVE, true,
                        new BigDecimal("60.00"), new BigDecimal("20.00"), new BigDecimal("30.00")));
        assertEquals("ALLOCATION_PRESET_MISMATCH", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(3, ex.getFieldErrors().size());
        assertEquals("essentialsPercentage", ex.getFieldErrors().get(0).field());
    }

    @Test
    void presetWithOnlyOneMismatchingPercentageIsRejected() {
        BusinessException ex = assertThrows(AllocationValidationException.class, () ->
                calculator.resolve(AllocationMode.CONSERVATIVE, true,
                        new BigDecimal("60.00"), new BigDecimal("20.00"), new BigDecimal("30.00")));
        assertEquals("ALLOCATION_PRESET_MISMATCH", ex.getCode());
        assertEquals(1, ex.getFieldErrors().size());
        assertEquals("safetyPercentage", ex.getFieldErrors().get(0).field());
    }

    @Test
    void manualWithExactlyHundredTotalIsAccepted() {
        AllocationCalculator.Percentages result = calculator.resolve(
                AllocationMode.MANUAL, true,
                new BigDecimal("45.50"), new BigDecimal("9.50"), new BigDecimal("45.00"));
        assertEquals(0, new BigDecimal("45.50").compareTo(result.essentials()));
        assertEquals(0, new BigDecimal("9.50").compareTo(result.safety()));
        assertEquals(0, new BigDecimal("45.00").compareTo(result.investments()));
        assertEquals(2, result.essentials().scale());
    }

    @Test
    void manualWithTotalNotHundredIsRejected() {
        BusinessException ex = assertThrows(AllocationValidationException.class, () ->
                calculator.resolve(AllocationMode.MANUAL, true,
                        new BigDecimal("55.00"), new BigDecimal("10.00"), new BigDecimal("30.00")));
        assertEquals("ALLOCATION_TOTAL_INVALID", ex.getCode());
        assertEquals(3, ex.getFieldErrors().size());
        ex.getFieldErrors().forEach(v -> assertEquals("TOTAL_MUST_EQUAL_100", v.code()));
    }

    @Test
    void manualPercentageOutOfRangeIsRejected() {
        BusinessException ex = assertThrows(AllocationValidationException.class, () ->
                calculator.resolve(AllocationMode.MANUAL, true,
                        new BigDecimal("150.00"), new BigDecimal("10.00"), new BigDecimal("-10.00")));
        assertEquals("ALLOCATION_PERCENTAGE_INVALID", ex.getCode());
        assertEquals(2, ex.getFieldErrors().size());
    }

    @Test
    void manualPercentageWithTooManyDecimalsIsRejected() {
        BusinessException ex = assertThrows(AllocationValidationException.class, () ->
                calculator.resolve(AllocationMode.MANUAL, true,
                        new BigDecimal("55.555"), new BigDecimal("10.00"), new BigDecimal("34.445")));
        assertEquals("ALLOCATION_PERCENTAGE_INVALID", ex.getCode());
        assertEquals(2, ex.getFieldErrors().size());
    }

    @Test
    void manualMissingPercentageIsRejected() {
        BusinessException ex = assertThrows(AllocationValidationException.class, () ->
                calculator.resolve(AllocationMode.MANUAL, true,
                        new BigDecimal("55.00"), null, new BigDecimal("45.00")));
        assertEquals("ALLOCATION_PERCENTAGE_INVALID", ex.getCode());
        assertEquals("safetyPercentage", ex.getFieldErrors().get(0).field());
        assertEquals("REQUIRED", ex.getFieldErrors().get(0).code());
    }

    @Test
    void investmentsOffWithPresetModeIsRejected() {
        BusinessException ex = assertThrows(AllocationValidationException.class, () ->
                calculator.resolve(AllocationMode.BALANCED, false,
                        new BigDecimal("55.00"), new BigDecimal("10.00"), new BigDecimal("35.00")));
        assertEquals("INVESTMENTS_OFF_REQUIRES_MANUAL", ex.getCode());
        assertEquals("allocationMode", ex.getFieldErrors().get(0).field());
    }

    @Test
    void investmentsOffAcceptsValidManualSplit() {
        AllocationCalculator.Percentages result = calculator.resolve(
                AllocationMode.MANUAL, false,
                new BigDecimal("70.00"), new BigDecimal("30.00"), null);
        assertEquals(0, new BigDecimal("70.00").compareTo(result.essentials()));
        assertEquals(0, new BigDecimal("30.00").compareTo(result.safety()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.investments()));
        assertEquals(2, result.investments().scale());
    }

    @Test
    void investmentsOffRejectsNonZeroInvestmentsPercentage() {
        BusinessException ex = assertThrows(AllocationValidationException.class, () ->
                calculator.resolve(AllocationMode.MANUAL, false,
                        new BigDecimal("70.00"), new BigDecimal("30.00"), new BigDecimal("10.00")));
        assertEquals("INVESTMENTS_MUST_BE_ZERO", ex.getCode());
        assertEquals("investmentsPercentage", ex.getFieldErrors().get(0).field());
    }

    @Test
    void investmentsOffRejectsTotalsOtherThanHundred() {
        BusinessException ex = assertThrows(AllocationValidationException.class, () ->
                calculator.resolve(AllocationMode.MANUAL, false,
                        new BigDecimal("70.00"), new BigDecimal("20.00"), null));
        assertEquals("ALLOCATION_TOTAL_INVALID", ex.getCode());
        assertEquals(3, ex.getFieldErrors().size());
    }

    @Test
    void nullModeIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                calculator.resolve(null, true, null, null, null));
        assertEquals("REQUEST_INVALID", ex.getCode());
    }

    @ParameterizedTest
    @CsvSource({
            "0.01, 55, 10, 35",
            "1.00, 60, 10, 30",
            "10000.01, 55, 10, 35",
            "30000.00, 50, 10, 40",
            "999999999999.99, 60, 10, 30",
            "0.01, 50, 50, 0",
            "10000.01, 0.01, 0.99, 99.00"
    })
    void derivedAmountsAlwaysSumExactlyToIncome(String income, String e, String s, String i) {
        BigDecimal incomeAmount = new BigDecimal(income);
        AllocationCalculator.Percentages percentages = new AllocationCalculator.Percentages(
                new BigDecimal(e), new BigDecimal(s), new BigDecimal(i));
        AllocationCalculator.Amounts amounts = calculator.calculate(incomeAmount, percentages, true);
        assertEquals(0, incomeAmount.compareTo(amounts.essentials().add(amounts.safety()).add(amounts.investments())),
                "bucket amounts must sum exactly to income for " + income);
        assertEquals(2, amounts.essentials().scale());
        assertEquals(2, amounts.safety().scale());
        assertEquals(2, amounts.investments().scale());
        assertTrue(amounts.investments().signum() >= 0, "investments amount must not be negative");
        assertTrue(amounts.essentials().signum() >= 0);
        assertTrue(amounts.safety().signum() >= 0);
    }

    @Test
    void roundingUsesHalfUpForEssentialsAndSafetyWithResidualInvestments() {
        AllocationCalculator.Percentages percentages = new AllocationCalculator.Percentages(
                new BigDecimal("55.00"), new BigDecimal("10.00"), new BigDecimal("35.00"));
        AllocationCalculator.Amounts amounts = calculator.calculate(new BigDecimal("10000.01"), percentages, true);
        assertEquals(0, new BigDecimal("5500.01").compareTo(amounts.essentials()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(amounts.safety()));
        assertEquals(0, new BigDecimal("3500.00").compareTo(amounts.investments()));
    }

    @Test
    void investmentsOffGivesEverythingBeyondEssentialsToSafety() {
        AllocationCalculator.Percentages percentages = new AllocationCalculator.Percentages(
                new BigDecimal("55.00"), new BigDecimal("45.00"), BigDecimal.ZERO);
        AllocationCalculator.Amounts amounts = calculator.calculate(new BigDecimal("10000.01"), percentages, false);
        assertEquals(0, new BigDecimal("5500.01").compareTo(amounts.essentials()));
        assertEquals(0, new BigDecimal("4500.00").compareTo(amounts.safety()));
        assertEquals(0, BigDecimal.ZERO.compareTo(amounts.investments()));
    }

    @Test
    void zeroIncomeProducesZeroAmounts() {
        AllocationCalculator.Percentages percentages = new AllocationCalculator.Percentages(
                new BigDecimal("55.00"), new BigDecimal("10.00"), new BigDecimal("35.00"));
        AllocationCalculator.Amounts amounts = calculator.calculate(BigDecimal.ZERO, percentages, true);
        assertEquals(0, BigDecimal.ZERO.compareTo(amounts.essentials()));
        assertEquals(0, BigDecimal.ZERO.compareTo(amounts.safety()));
        assertEquals(0, BigDecimal.ZERO.compareTo(amounts.investments()));
        assertEquals(2, amounts.essentials().scale());
    }

    @Test
    void fullHundredPercentToOneBucketIsExact() {
        AllocationCalculator.Percentages percentages = new AllocationCalculator.Percentages(
                new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        AllocationCalculator.Amounts amounts = calculator.calculate(new BigDecimal("0.01"), percentages, true);
        assertEquals(0, new BigDecimal("0.01").compareTo(amounts.essentials()));
        assertEquals(0, BigDecimal.ZERO.compareTo(amounts.safety()));
        assertEquals(0, BigDecimal.ZERO.compareTo(amounts.investments()));
    }
}
