package com.budgetingapp.dto.response;

import com.budgetingapp.entity.enums.BudgetBucket;
import com.budgetingapp.entity.enums.EntryType;

import java.util.List;

public class CategoryResponse {

    private String code;

    private String label;

    private List<EntryType> allowedEntryTypes;

    private List<BudgetBucket> allowedBuckets;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<EntryType> getAllowedEntryTypes() {
        return allowedEntryTypes;
    }

    public void setAllowedEntryTypes(List<EntryType> allowedEntryTypes) {
        this.allowedEntryTypes = allowedEntryTypes;
    }

    public List<BudgetBucket> getAllowedBuckets() {
        return allowedBuckets;
    }

    public void setAllowedBuckets(List<BudgetBucket> allowedBuckets) {
        this.allowedBuckets = allowedBuckets;
    }
}
