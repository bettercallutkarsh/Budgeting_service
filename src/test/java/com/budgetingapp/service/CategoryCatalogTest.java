package com.budgetingapp.service;

import com.budgetingapp.entity.enums.BudgetBucket;
import com.budgetingapp.entity.enums.EntryType;
import com.budgetingapp.service.CategoryCatalog.CategoryDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryCatalogTest {

    @Test
    void fullCatalogContainsTwentyTwoUniqueCodesWithHumanLabels() {
        List<CategoryDefinition> catalog = CategoryCatalog.catalog(null, null);

        assertEquals(22, catalog.size());
        Set<String> codes = catalog.stream().map(CategoryDefinition::code).collect(Collectors.toSet());
        assertEquals(22, codes.size());
        for (CategoryDefinition definition : catalog) {
            assertNotNull(definition.label());
            assertFalse(definition.label().isBlank());
        }
        assertTrue(codes.contains("GROCERIES"));
        assertTrue(codes.contains("CREDIT_CARD_BILL_PAYMENT"));
        assertEquals("Groceries", find("GROCERIES").label());
        assertEquals("Pay credit-card bill", find("CREDIT_CARD_BILL_PAYMENT").label());
    }

    @Test
    void expenseEssentialsFilterReturnsTenCodesWithoutInvestmentsOrCardPayment() {
        List<CategoryDefinition> catalog = CategoryCatalog.catalog(EntryType.EXPENSE, BudgetBucket.ESSENTIALS);

        assertEquals(10, catalog.size());
        Set<String> codes = codesOf(catalog);
        assertTrue(codes.containsAll(List.of("GROCERIES", "RENT", "UTILITIES", "TRANSPORT",
                "FOOD_DINING", "MEDICAL", "EDUCATION", "SHOPPING", "SUBSCRIPTIONS", "OTHER_EXPENSE")));
        assertFalse(codes.contains("SIP"));
        assertFalse(codes.contains("CREDIT_CARD_BILL_PAYMENT"));
        for (CategoryDefinition definition : catalog) {
            assertEquals(Set.of(EntryType.EXPENSE), definition.entryTypes());
            assertTrue(definition.buckets().contains(BudgetBucket.ESSENTIALS));
        }
    }

    @Test
    void dualBucketExpenseCodesAppearUnderBothBuckets() {
        CategoryDefinition medical = find("MEDICAL");
        assertEquals(Set.of(EntryType.EXPENSE), medical.entryTypes());
        assertEquals(Set.of(BudgetBucket.ESSENTIALS, BudgetBucket.SAFETY), medical.buckets());

        List<CategoryDefinition> safetyCatalog = CategoryCatalog.catalog(null, BudgetBucket.SAFETY);
        Set<String> safetyCodes = codesOf(safetyCatalog);
        assertEquals(Set.of("MEDICAL", "OTHER_EXPENSE", "EMERGENCY_FUND", "SAFETY_SAVING"), safetyCodes);
    }

    @Test
    void safetySavingFilterReturnsOnlySafetyCodes() {
        List<CategoryDefinition> catalog = CategoryCatalog.catalog(EntryType.SAFETY_SAVING, BudgetBucket.SAFETY);

        assertEquals(Set.of("EMERGENCY_FUND", "SAFETY_SAVING"), codesOf(catalog));
        for (CategoryDefinition definition : catalog) {
            assertEquals(Set.of(EntryType.SAFETY_SAVING), definition.entryTypes());
        }
    }

    @Test
    void investmentFilterReturnsNineInvestmentCodes() {
        List<CategoryDefinition> catalog = CategoryCatalog.catalog(EntryType.INVESTMENT, BudgetBucket.INVESTMENTS);

        assertEquals(Set.of("SIP", "MUTUAL_FUND_LUMPSUM", "STOCKS", "FD", "RD", "PPF",
                "GOLD", "GOLD_SIP", "OTHER_INVESTMENT"), codesOf(catalog));
        for (CategoryDefinition definition : catalog) {
            assertEquals(Set.of(BudgetBucket.INVESTMENTS), definition.buckets());
        }
        Set<String> labels = catalog.stream().map(CategoryDefinition::label).collect(Collectors.toSet());
        assertEquals(catalog.size(), labels.size(), "Every investment option must have a distinct label");
        assertEquals("Gold SIP", find("GOLD_SIP").label());
        assertTrue(codesOf(catalog).containsAll(List.of("SIP", "MUTUAL_FUND_LUMPSUM", "STOCKS",
                "FD", "RD", "PPF", "GOLD", "OTHER_INVESTMENT")));
    }

    @Test
    void creditCardBillHasSingleTypeAndNoBuckets() {
        CategoryDefinition definition = find("CREDIT_CARD_BILL_PAYMENT");

        assertEquals(Set.of(EntryType.CREDIT_CARD_PAYMENT), definition.entryTypes());
        assertTrue(definition.buckets().isEmpty());
    }

    @Test
    void catalogFilterIntersectsTypeAndBucket() {
        assertTrue(CategoryCatalog.catalog(EntryType.SAFETY_SAVING, BudgetBucket.ESSENTIALS).isEmpty());
        assertTrue(CategoryCatalog.catalog(EntryType.EXPENSE, BudgetBucket.INVESTMENTS).isEmpty());
        assertTrue(CategoryCatalog.catalog(EntryType.CREDIT_CARD_PAYMENT, BudgetBucket.SAFETY).isEmpty());
    }

    @Test
    void allowedCategoriesMatchCatalogFilterForEveryTypeBucketCombination() {
        for (EntryType entryType : EntryType.values()) {
            for (BudgetBucket bucket : new BudgetBucket[]{BudgetBucket.ESSENTIALS,
                    BudgetBucket.SAFETY, BudgetBucket.INVESTMENTS}) {
                Set<String> allowed = CategoryCatalog.allowedCategories(entryType, bucket);
                Set<String> catalogCodes = codesOf(CategoryCatalog.catalog(entryType, bucket));
                assertEquals(catalogCodes, allowed,
                        "Mismatch for " + entryType + "/" + bucket);
            }
        }
    }

    @Test
    void isKnownMatchesExactlyTheCatalogCodes() {
        Set<String> catalogCodes = codesOf(CategoryCatalog.catalog(null, null));
        for (String code : catalogCodes) {
            assertTrue(CategoryCatalog.isKnown(code), code + " should be known");
        }
        assertFalse(CategoryCatalog.isKnown("GROCERIES_EXTRA"));
        assertFalse(CategoryCatalog.isKnown(null));
        assertFalse(CategoryCatalog.isKnown(""));
    }

    private CategoryDefinition find(String code) {
        return CategoryCatalog.catalog(null, null).stream()
                .filter(definition -> definition.code().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private Set<String> codesOf(List<CategoryDefinition> catalog) {
        return catalog.stream().map(CategoryDefinition::code).collect(Collectors.toSet());
    }
}
