package com.budgetingapp.service;

import com.budgetingapp.entity.SipPlan;
import com.budgetingapp.entity.enums.SipInstallmentStatus;
import com.budgetingapp.entity.enums.SipPlanStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SipScheduleCalculatorTest {

    private final SipScheduleCalculator calculator = new SipScheduleCalculator();

    @Test
    void clampsInstallmentDayToMonthLength() {
        assertEquals(LocalDate.of(2026, 2, 28),
                calculator.scheduledDate(YearMonth.of(2026, 2), 31));
        assertEquals(LocalDate.of(2028, 2, 29),
                calculator.scheduledDate(YearMonth.of(2028, 2), 31));
        assertEquals(LocalDate.of(2026, 4, 30),
                calculator.scheduledDate(YearMonth.of(2026, 4), 31));
    }

    @Test
    void firstInstallmentStartsOnOrAfterTrackingStart() {
        assertEquals(LocalDate.of(2026, 3, 10),
                calculator.firstInstallmentDate(LocalDate.of(2026, 2, 11), 10));
        assertEquals(LocalDate.of(2026, 2, 10),
                calculator.firstInstallmentDate(LocalDate.of(2026, 2, 10), 10));
        assertEquals(LocalDate.of(2026, 2, 28),
                calculator.firstInstallmentDate(LocalDate.of(2026, 2, 28), 31));
    }

    @Test
    void scheduleRespectsTrackingAndPlannedEnd() {
        SipPlan plan = plan(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 11), 10);
        plan.setPlannedEndDate(LocalDate.of(2026, 4, 30));

        List<LocalDate> dates = calculator.scheduledDates(plan,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertEquals(List.of(
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 4, 10)), dates);
    }

    @Test
    void scheduleUsesEarlierOfPlannedAndActualEnd() {
        SipPlan plan = plan(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), 5);
        plan.setPlannedEndDate(LocalDate.of(2026, 12, 31));
        plan.setActualEndedDate(LocalDate.of(2026, 3, 4));

        List<LocalDate> dates = calculator.scheduledDates(plan,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertEquals(List.of(
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 2, 5)), dates);
    }

    @Test
    void openEndedScheduleContinuesThroughRequestedRange() {
        SipPlan plan = plan(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), 1);

        List<LocalDate> dates = calculator.scheduledDates(plan,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertEquals(3, dates.size());
    }

    @Test
    void isScheduledRejectsPeriodBeforeTrackingStart() {
        SipPlan plan = plan(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 11), 10);

        assertFalse(calculator.isScheduled(plan, YearMonth.of(2026, 2)));
        assertTrue(calculator.isScheduled(plan, YearMonth.of(2026, 3)));
    }

    @Test
    void installmentStatusUsesIstDateComparison() {
        LocalDate today = LocalDate.of(2026, 10, 31);

        assertEquals(SipInstallmentStatus.PAID,
                calculator.installmentStatus(today, true, today));
        assertEquals(SipInstallmentStatus.OVERDUE,
                calculator.installmentStatus(today.minusDays(1), false, today));
        assertEquals(SipInstallmentStatus.DUE,
                calculator.installmentStatus(today, false, today));
        assertEquals(SipInstallmentStatus.UPCOMING,
                calculator.installmentStatus(today.plusDays(1), false, today));
    }

    @Test
    void completedStatusIsDerivedFromPlannedEndDate() {
        SipPlan plan = plan(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), 1);
        plan.setPlannedEndDate(LocalDate.of(2026, 10, 30));

        assertEquals(SipPlanStatus.COMPLETED,
                calculator.effectiveStatus(plan, LocalDate.of(2026, 10, 31)));
        assertEquals(SipPlanStatus.ACTIVE,
                calculator.effectiveStatus(plan, LocalDate.of(2026, 10, 30)));
    }

    @Test
    void stoppedStatusWinsOverCompletedDerivation() {
        SipPlan plan = plan(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), 1);
        plan.setStatus(SipPlanStatus.STOPPED);
        plan.setActualEndedDate(LocalDate.of(2026, 2, 1));
        plan.setPlannedEndDate(LocalDate.of(2026, 3, 1));

        assertEquals(SipPlanStatus.STOPPED,
                calculator.effectiveStatus(plan, LocalDate.of(2026, 12, 31)));
    }

    private SipPlan plan(LocalDate originalStart, LocalDate trackingStart, int day) {
        SipPlan plan = new SipPlan();
        plan.setOriginalStartDate(originalStart);
        plan.setTrackingStartDate(trackingStart);
        plan.setInstallmentDay(day);
        plan.setStatus(SipPlanStatus.ACTIVE);
        return plan;
    }
}
