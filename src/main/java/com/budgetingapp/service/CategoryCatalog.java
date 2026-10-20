package com.budgetingapp.service;

import com.budgetingapp.entity.enums.BudgetBucket;
import com.budgetingapp.entity.enums.EntryType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CategoryCatalog {

    public static final String CREDIT_CARD_BILL_PAYMENT = "CREDIT_CARD_BILL_PAYMENT";

    private static final Set<String> EXPENSE_ESSENTIALS = Set.of(
            "GROCERIES", "RENT", "UTILITIES", "TRANSPORT", "FOOD_DINING",
            "MEDICAL", "EDUCATION", "SHOPPING", "SUBSCRIPTIONS", "OTHER_EXPENSE");

    private static final Set<String> EXPENSE_SAFETY = Set.of("MEDICAL", "OTHER_EXPENSE");

    private static final Set<String> SAFETY_SAVING_SAFETY = Set.of("EMERGENCY_FUND", "SAFETY_SAVING");

    private static final Set<String> INVESTMENT_INVESTMENTS = Set.of(
            "SIP", "FD", "STOCKS", "GOLD_SIP", "OTHER_INVESTMENT");

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("GROCERIES", "Groceries"),
            Map.entry("RENT", "Rent"),
            Map.entry("UTILITIES", "Utilities"),
            Map.entry("TRANSPORT", "Transport"),
            Map.entry("FOOD_DINING", "Food & Dining"),
            Map.entry("MEDICAL", "Medical"),
            Map.entry("EDUCATION", "Education"),
            Map.entry("SHOPPING", "Shopping"),
            Map.entry("SUBSCRIPTIONS", "Subscriptions"),
            Map.entry("OTHER_EXPENSE", "Other expense"),
            Map.entry("EMERGENCY_FUND", "Emergency fund"),
            Map.entry("SAFETY_SAVING", "Safety saving"),
            Map.entry("SIP", "SIP"),
            Map.entry("FD", "FD"),
            Map.entry("STOCKS", "Stocks"),
            Map.entry("GOLD_SIP", "Gold SIP"),
            Map.entry("OTHER_INVESTMENT", "Other investment"),
            Map.entry(CREDIT_CARD_BILL_PAYMENT, "Pay credit-card bill"));

    public record CategoryDefinition(String code, String label,
                                     Set<EntryType> entryTypes, Set<BudgetBucket> buckets) {
    }

    private static final List<CategoryDefinition> CATALOG = buildCatalog();

    private CategoryCatalog() {
    }

    public static boolean isKnown(String categoryCode) {
        if (categoryCode == null) {
            return false;
        }
        return EXPENSE_ESSENTIALS.contains(categoryCode)
                || EXPENSE_SAFETY.contains(categoryCode)
                || SAFETY_SAVING_SAFETY.contains(categoryCode)
                || INVESTMENT_INVESTMENTS.contains(categoryCode)
                || CREDIT_CARD_BILL_PAYMENT.equals(categoryCode);
    }

    public static Set<String> allowedCategories(EntryType entryType, BudgetBucket bucket) {
        if (entryType == null) {
            return Set.of();
        }
        return switch (entryType) {
            case EXPENSE -> {
                if (bucket == BudgetBucket.ESSENTIALS) {
                    yield EXPENSE_ESSENTIALS;
                }
                if (bucket == BudgetBucket.SAFETY) {
                    yield EXPENSE_SAFETY;
                }
                yield Set.of();
            }
            case SAFETY_SAVING -> bucket == BudgetBucket.SAFETY ? SAFETY_SAVING_SAFETY : Set.of();
            case INVESTMENT -> bucket == BudgetBucket.INVESTMENTS ? INVESTMENT_INVESTMENTS : Set.of();
            case CREDIT_CARD_PAYMENT -> bucket == null ? Set.of(CREDIT_CARD_BILL_PAYMENT) : Set.of();
        };
    }

    public static List<CategoryDefinition> catalog(EntryType entryType, BudgetBucket bucket) {
        List<CategoryDefinition> result = new ArrayList<>();
        for (CategoryDefinition definition : CATALOG) {
            if (entryType != null && !definition.entryTypes().contains(entryType)) {
                continue;
            }
            if (bucket != null && !definition.buckets().contains(bucket)) {
                continue;
            }
            result.add(definition);
        }
        return List.copyOf(result);
    }

    private static List<CategoryDefinition> buildCatalog() {
        Map<String, Set<EntryType>> types = new LinkedHashMap<>();
        Map<String, Set<BudgetBucket>> buckets = new LinkedHashMap<>();
        collect(types, buckets, EXPENSE_ESSENTIALS, EntryType.EXPENSE, BudgetBucket.ESSENTIALS);
        collect(types, buckets, EXPENSE_SAFETY, EntryType.EXPENSE, BudgetBucket.SAFETY);
        collect(types, buckets, SAFETY_SAVING_SAFETY, EntryType.SAFETY_SAVING, BudgetBucket.SAFETY);
        collect(types, buckets, INVESTMENT_INVESTMENTS, EntryType.INVESTMENT, BudgetBucket.INVESTMENTS);
        types.put(CREDIT_CARD_BILL_PAYMENT, EnumSet.of(EntryType.CREDIT_CARD_PAYMENT));
        buckets.put(CREDIT_CARD_BILL_PAYMENT, EnumSet.noneOf(BudgetBucket.class));
        List<CategoryDefinition> definitions = new ArrayList<>();
        types.forEach((code, entryTypes) ->
                definitions.add(new CategoryDefinition(code, LABELS.get(code),
                        Set.copyOf(entryTypes), Set.copyOf(buckets.get(code)))));
        return List.copyOf(definitions);
    }

    private static void collect(Map<String, Set<EntryType>> types, Map<String, Set<BudgetBucket>> buckets,
                                Set<String> codes, EntryType entryType, BudgetBucket bucket) {
        for (String code : codes) {
            types.computeIfAbsent(code, key -> EnumSet.noneOf(EntryType.class)).add(entryType);
            buckets.computeIfAbsent(code, key -> EnumSet.noneOf(BudgetBucket.class)).add(bucket);
        }
    }
}
