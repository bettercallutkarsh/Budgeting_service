package com.budgetingapp.dto;

import com.budgetingapp.dto.request.MonthlyBudgetSaveRequest;
import com.budgetingapp.entity.enums.AllocationMode;
import com.budgetingapp.entity.enums.IncomeType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonthlyBudgetSaveRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    @Test
    void validPresetRequestHasNoViolations() {
        Set<ConstraintViolation<MonthlyBudgetSaveRequest>> violations = validate(validRequest());
        assertTrue(violations.isEmpty());
    }

    @Test
    void missingRequiredFieldsProduceViolations() {
        MonthlyBudgetSaveRequest request = new MonthlyBudgetSaveRequest();
        Set<String> fields = fieldsWithViolations(request);
        assertEquals(Set.of("incomeType", "incomeAmount", "allocationMode", "investmentsEnabled"), fields);
    }

    @Test
    void negativeIncomeIsRejected() {
        MonthlyBudgetSaveRequest request = validRequest();
        request.setIncomeAmount(new BigDecimal("-0.01"));
        Set<String> fields = fieldsWithViolations(request);
        assertEquals(Set.of("incomeAmount"), fields);
    }

    @Test
    void incomeWithThreeDecimalPlacesIsRejected() {
        MonthlyBudgetSaveRequest request = validRequest();
        request.setIncomeAmount(new BigDecimal("30000.005"));
        assertEquals(Set.of("incomeAmount"), fieldsWithViolations(request));
    }

    @Test
    void percentageAboveHundredIsRejected() {
        MonthlyBudgetSaveRequest request = validRequest();
        request.setEssentialsPercentage(new BigDecimal("100.01"));
        assertEquals(Set.of("essentialsPercentage"), fieldsWithViolations(request));
    }

    @Test
    void percentageWithThreeDecimalPlacesIsRejected() {
        MonthlyBudgetSaveRequest request = validRequest();
        request.setSafetyPercentage(new BigDecimal("10.005"));
        assertEquals(Set.of("safetyPercentage"), fieldsWithViolations(request));
    }

    @Test
    void negativePercentageIsRejected() {
        MonthlyBudgetSaveRequest request = validRequest();
        request.setInvestmentsPercentage(new BigDecimal("-1.00"));
        assertEquals(Set.of("investmentsPercentage"), fieldsWithViolations(request));
    }

    @Test
    void oversizedIncomeSourceLabelIsRejected() {
        MonthlyBudgetSaveRequest request = validRequest();
        request.setIncomeSourceLabel("x".repeat(121));
        assertEquals(Set.of("incomeSourceLabel"), fieldsWithViolations(request));
    }

    @Test
    void manualRequestWithAllPercentagesIsValid() {
        MonthlyBudgetSaveRequest request = validRequest();
        request.setAllocationMode(AllocationMode.MANUAL);
        request.setEssentialsPercentage(new BigDecimal("45.50"));
        request.setSafetyPercentage(new BigDecimal("9.50"));
        request.setInvestmentsPercentage(new BigDecimal("45.00"));
        assertTrue(validate(request).isEmpty());
    }

    @Test
    void presetRequestWithoutPercentagesIsValid() {
        MonthlyBudgetSaveRequest request = validRequest();
        assertFalse(fieldsWithViolations(request).contains("essentialsPercentage"));
    }

    private MonthlyBudgetSaveRequest validRequest() {
        MonthlyBudgetSaveRequest request = new MonthlyBudgetSaveRequest();
        request.setIncomeType(IncomeType.SALARY);
        request.setIncomeAmount(new BigDecimal("30000.00"));
        request.setAllocationMode(AllocationMode.BALANCED);
        request.setInvestmentsEnabled(Boolean.TRUE);
        return request;
    }

    private Set<ConstraintViolation<MonthlyBudgetSaveRequest>> validate(MonthlyBudgetSaveRequest request) {
        return validator.validate(request);
    }

    private Set<String> fieldsWithViolations(MonthlyBudgetSaveRequest request) {
        return validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
