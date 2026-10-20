package com.budgetingapp.dto.request;

import com.budgetingapp.entity.enums.BudgetBucket;
import com.budgetingapp.entity.enums.EntryStatus;
import com.budgetingapp.entity.enums.EntryType;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;

public class MoneyEntryListQuery {

    private LocalDate fromDate;

    private LocalDate toDate;

    private EntryType entryType;

    private BudgetBucket bucket;

    private String categoryCode;

    private EntryStatus status;

    @Min(value = 0, message = "page must not be negative")
    private Integer page;

    @Min(value = 1, message = "size must be at least 1")
    private Integer size;

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public BudgetBucket getBucket() {
        return bucket;
    }

    public void setBucket(BudgetBucket bucket) {
        this.bucket = bucket;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public EntryStatus getStatus() {
        return status;
    }

    public void setStatus(EntryStatus status) {
        this.status = status;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }
}
