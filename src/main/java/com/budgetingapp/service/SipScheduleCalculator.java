package com.budgetingapp.service;

import com.budgetingapp.entity.SipPlan;
import com.budgetingapp.entity.enums.SipInstallmentStatus;
import com.budgetingapp.entity.enums.SipPlanStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Component
public class SipScheduleCalculator {

    public LocalDate scheduledDate(YearMonth month, int installmentDay) {
        return month.atDay(Math.min(installmentDay, month.lengthOfMonth()));
    }

    public LocalDate firstInstallmentDate(LocalDate trackingStartDate, int installmentDay) {
        YearMonth month = YearMonth.from(trackingStartDate);
        LocalDate candidate = scheduledDate(month, installmentDay);
        if (candidate.isBefore(trackingStartDate)) {
            candidate = scheduledDate(month.plusMonths(1), installmentDay);
        }
        return candidate;
    }

    public LocalDate effectiveEndDate(SipPlan plan) {
        LocalDate planned = plan.getPlannedEndDate();
        LocalDate actual = plan.getActualEndedDate();
        if (planned == null) {
            return actual;
        }
        if (actual == null) {
            return planned;
        }
        return actual.isBefore(planned) ? actual : planned;
    }

    public boolean isScheduled(SipPlan plan, YearMonth period) {
        LocalDate dueDate = scheduledDate(period, plan.getInstallmentDay());
        LocalDate first = firstInstallmentDate(plan.getTrackingStartDate(), plan.getInstallmentDay());
        LocalDate end = effectiveEndDate(plan);
        return !dueDate.isBefore(first) && (end == null || !dueDate.isAfter(end));
    }

    public List<LocalDate> scheduledDates(SipPlan plan, LocalDate fromDate, LocalDate toDate) {
        LocalDate first = firstInstallmentDate(plan.getTrackingStartDate(), plan.getInstallmentDay());
        LocalDate end = effectiveEndDate(plan);
        LocalDate effectiveStart = fromDate.isAfter(first) ? fromDate : first;
        LocalDate effectiveEnd = toDate;
        if (end != null && end.isBefore(effectiveEnd)) {
            effectiveEnd = end;
        }
        if (effectiveStart.isAfter(effectiveEnd)) {
            return List.of();
        }
        YearMonth startMonth = YearMonth.from(effectiveStart);
        YearMonth endMonth = YearMonth.from(effectiveEnd);
        List<LocalDate> dates = new ArrayList<>();
        for (YearMonth month = startMonth; !month.isAfter(endMonth); month = month.plusMonths(1)) {
            LocalDate dueDate = scheduledDate(month, plan.getInstallmentDay());
            if (!dueDate.isBefore(first) && !dueDate.isBefore(fromDate) && !dueDate.isAfter(toDate)
                    && (end == null || !dueDate.isAfter(end))) {
                dates.add(dueDate);
            }
        }
        return List.copyOf(dates);
    }

    public SipInstallmentStatus installmentStatus(LocalDate dueDate, boolean paid, LocalDate today) {
        if (paid) {
            return SipInstallmentStatus.PAID;
        }
        if (dueDate.isBefore(today)) {
            return SipInstallmentStatus.OVERDUE;
        }
        if (dueDate.isEqual(today)) {
            return SipInstallmentStatus.DUE;
        }
        return SipInstallmentStatus.UPCOMING;
    }

    public SipPlanStatus effectiveStatus(SipPlan plan, LocalDate today) {
        if (plan.getStatus() == SipPlanStatus.STOPPED) {
            return SipPlanStatus.STOPPED;
        }
        if (plan.getStatus() == SipPlanStatus.COMPLETED) {
            return SipPlanStatus.COMPLETED;
        }
        if (plan.getPlannedEndDate() != null && today.isAfter(plan.getPlannedEndDate())) {
            return SipPlanStatus.COMPLETED;
        }
        return SipPlanStatus.ACTIVE;
    }
}
