package com.budgetingapp.dto.request;

import java.time.LocalDate;

public class SipPlanStopRequest {

    private LocalDate stopDate;

    public LocalDate getStopDate() {
        return stopDate;
    }

    public void setStopDate(LocalDate stopDate) {
        this.stopDate = stopDate;
    }
}
