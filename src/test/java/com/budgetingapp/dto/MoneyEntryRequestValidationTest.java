package com.budgetingapp.dto;

import com.budgetingapp.dto.request.CreditCardOpeningRequest;
import com.budgetingapp.dto.request.MoneyEntryCreateRequest;
import com.budgetingapp.dto.request.MoneyEntryListQuery;
import com.budgetingapp.dto.request.MoneyEntryVoidRequest;
import com.budgetingapp.entity.enums.BudgetBucket;
import com.budgetingapp.entity.enums.EntryType;
import com.budgetingapp.entity.enums.PaymentMethod;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyEntryRequestValidationTest {

    private static final String VALID_UUID = "8f14e45f-ea5c-4f7f-9d3e-2a6b7c8d9e0f";

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
    void validCreateRequestHasNoViolations() {
        assertTrue(validate(validCreateRequest()).isEmpty());
    }

    @Test
    void missingRequiredCreateFieldsProduceViolations() {
        MoneyEntryCreateRequest request = new MoneyEntryCreateRequest();
        assertEquals(Set.of("entryDate", "entryTime", "entryType", "categoryCode", "amount"),
                fieldsWithViolations(request));
    }

    @Test
    void zeroAmountIsRejected() {
        MoneyEntryCreateRequest request = validCreateRequest();
        request.setAmount(BigDecimal.ZERO);
        assertEquals(Set.of("amount"), fieldsWithViolations(request));
    }

    @Test
    void negativeAmountIsRejected() {
        MoneyEntryCreateRequest request = validCreateRequest();
        request.setAmount(new BigDecimal("-10.00"));
        assertEquals(Set.of("amount"), fieldsWithViolations(request));
    }

    @Test
    void amountWithThreeDecimalPlacesIsRejected() {
        MoneyEntryCreateRequest request = validCreateRequest();
        request.setAmount(new BigDecimal("10.999"));
        assertEquals(Set.of("amount"), fieldsWithViolations(request));
    }

    @Test
    void lowercaseCategoryCodeIsRejected() {
        MoneyEntryCreateRequest request = validCreateRequest();
        request.setCategoryCode("groceries");
        assertEquals(Set.of("categoryCode"), fieldsWithViolations(request));
    }

    @Test
    void oversizedCategoryCodeIsRejected() {
        MoneyEntryCreateRequest request = validCreateRequest();
        request.setCategoryCode("G" + "X".repeat(80));
        assertEquals(Set.of("categoryCode"), fieldsWithViolations(request));
    }

    @Test
    void nonUuidClientRequestIdIsRejected() {
        MoneyEntryCreateRequest request = validCreateRequest();
        request.setClientRequestId("not-a-uuid");
        assertEquals(Set.of("clientRequestId"), fieldsWithViolations(request));
    }

    @Test
    void validUuidClientRequestIdIsAccepted() {
        MoneyEntryCreateRequest request = validCreateRequest();
        request.setClientRequestId(VALID_UUID);
        assertTrue(validate(request).isEmpty());
    }

    @Test
    void oversizedNoteIsRejected() {
        MoneyEntryCreateRequest request = validCreateRequest();
        request.setNote("n".repeat(501));
        assertEquals(Set.of("note"), fieldsWithViolations(request));
    }

    @Test
    void maximumLengthNoteIsValid() {
        MoneyEntryCreateRequest request = validCreateRequest();
        request.setNote("n".repeat(500));
        assertTrue(validate(request).isEmpty());
    }

    @Test
    void validVoidRequestHasNoViolations() {
        MoneyEntryVoidRequest request = new MoneyEntryVoidRequest();
        request.setVoidReason("r".repeat(250));
        assertTrue(validate(request).isEmpty());
    }

    @Test
    void oversizedVoidReasonIsRejected() {
        MoneyEntryVoidRequest request = new MoneyEntryVoidRequest();
        request.setVoidReason("r".repeat(251));
        assertEquals(Set.of("voidReason"), fieldsWithViolations(request));
    }

    @Test
    void validListQueryHasNoViolations() {
        MoneyEntryListQuery query = new MoneyEntryListQuery();
        query.setFromDate(LocalDate.of(2026, 10, 1));
        query.setToDate(LocalDate.of(2026, 10, 18));
        query.setEntryType(EntryType.EXPENSE);
        query.setBucket(BudgetBucket.ESSENTIALS);
        query.setCategoryCode("GROCERIES");
        query.setPage(0);
        query.setSize(10);
        assertTrue(validate(query).isEmpty());
    }

    @Test
    void emptyListQueryHasNoViolations() {
        assertTrue(validate(new MoneyEntryListQuery()).isEmpty());
    }

    @Test
    void negativePageIsRejected() {
        MoneyEntryListQuery query = new MoneyEntryListQuery();
        query.setPage(-1);
        assertEquals(Set.of("page"), fieldsWithViolations(query));
    }

    @Test
    void zeroSizeIsRejected() {
        MoneyEntryListQuery query = new MoneyEntryListQuery();
        query.setSize(0);
        assertEquals(Set.of("size"), fieldsWithViolations(query));
    }

    @Test
    void validCreditCardOpeningRequestHasNoViolations() {
        CreditCardOpeningRequest request = new CreditCardOpeningRequest();
        request.setOpeningOutstandingAmount(new BigDecimal("5000.00"));
        request.setTrackingStartedAtUtc(Instant.parse("2026-10-01T00:00:00Z"));
        assertTrue(validate(request).isEmpty());
    }

    @Test
    void missingOpeningAmountIsRejected() {
        CreditCardOpeningRequest request = new CreditCardOpeningRequest();
        assertEquals(Set.of("openingOutstandingAmount"), fieldsWithViolations(request));
    }

    @Test
    void negativeOpeningAmountIsRejected() {
        CreditCardOpeningRequest request = new CreditCardOpeningRequest();
        request.setOpeningOutstandingAmount(new BigDecimal("-0.01"));
        assertEquals(Set.of("openingOutstandingAmount"), fieldsWithViolations(request));
    }

    @Test
    void openingAmountWithThreeDecimalPlacesIsRejected() {
        CreditCardOpeningRequest request = new CreditCardOpeningRequest();
        request.setOpeningOutstandingAmount(new BigDecimal("10.999"));
        assertEquals(Set.of("openingOutstandingAmount"), fieldsWithViolations(request));
    }

    private MoneyEntryCreateRequest validCreateRequest() {
        MoneyEntryCreateRequest request = new MoneyEntryCreateRequest();
        request.setEntryDate(LocalDate.of(2026, 10, 18));
        request.setEntryTime(LocalTime.of(12, 0));
        request.setEntryType(EntryType.EXPENSE);
        request.setBucket(BudgetBucket.ESSENTIALS);
        request.setCategoryCode("GROCERIES");
        request.setAmount(new BigDecimal("250.50"));
        request.setPaymentMethod(PaymentMethod.UPI);
        return request;
    }

    private <T> Set<ConstraintViolation<T>> validate(T request) {
        return validator.validate(request);
    }

    private <T> Set<String> fieldsWithViolations(T request) {
        return validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
