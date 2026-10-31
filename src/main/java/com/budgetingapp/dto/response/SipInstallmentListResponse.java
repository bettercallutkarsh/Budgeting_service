package com.budgetingapp.dto.response;

import java.time.LocalDate;
import java.util.List;

public class SipInstallmentListResponse {

    private LocalDate fromDate;

    private LocalDate toDate;

    private List<SipInstallmentResponse> content;

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

    public List<SipInstallmentResponse> getContent() {
        return content;
    }

    public void setContent(List<SipInstallmentResponse> content) {
        this.content = content;
    }
}
