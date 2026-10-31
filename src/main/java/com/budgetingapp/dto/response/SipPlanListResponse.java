package com.budgetingapp.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.List;

public class SipPlanListResponse {

    private List<SipPlanResponse> content;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal activeMonthlyCommitment;

    public List<SipPlanResponse> getContent() {
        return content;
    }

    public void setContent(List<SipPlanResponse> content) {
        this.content = content;
    }

    public BigDecimal getActiveMonthlyCommitment() {
        return activeMonthlyCommitment;
    }

    public void setActiveMonthlyCommitment(BigDecimal activeMonthlyCommitment) {
        this.activeMonthlyCommitment = activeMonthlyCommitment;
    }
}
