package com.budgetingapp.repository;

import com.budgetingapp.entity.SipPlan;
import com.budgetingapp.entity.enums.SipPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface SipPlanRepository extends JpaRepository<SipPlan, Long> {

    List<SipPlan> findByUser_UserIdOrderByCreatedAtUtcDescSipPlanIdDesc(Long userId);

    Optional<SipPlan> findBySipPlanIdAndUser_UserId(Long sipPlanId, Long userId);

    List<SipPlan> findByUser_UserIdAndStatusOrderByCreatedAtUtcDescSipPlanIdDesc(
            Long userId, SipPlanStatus status);

    @Query("select coalesce(sum(p.monthlyAmount), 0) from SipPlan p "
            + "where p.user.userId = :userId and p.status = :status")
    BigDecimal sumMonthlyAmountByUserAndStatus(@Param("userId") Long userId,
                                               @Param("status") SipPlanStatus status);
}
