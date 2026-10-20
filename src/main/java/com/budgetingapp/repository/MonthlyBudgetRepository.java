package com.budgetingapp.repository;

import com.budgetingapp.entity.MonthlyBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudget, Long> {

    Optional<MonthlyBudget> findByUser_UserIdAndBudgetMonth(Long userId, LocalDate budgetMonth);

    boolean existsByUser_UserIdAndBudgetMonth(Long userId, LocalDate budgetMonth);

    List<MonthlyBudget> findByUser_UserIdOrderByBudgetMonthAsc(Long userId);

    @Query("select coalesce(sum(b.incomeAmount), 0) from MonthlyBudget b "
            + "where b.user.userId = :userId and b.budgetMonth >= :fromMonth "
            + "and b.budgetMonth <= :toMonth and b.budgetMonth <= :currentMonth")
    BigDecimal sumIncomeByUser_UserIdAndBudgetMonthBetweenAndBudgetMonthLessThanEqual(
            @Param("userId") Long userId, @Param("fromMonth") LocalDate fromMonth,
            @Param("toMonth") LocalDate toMonth, @Param("currentMonth") LocalDate currentMonth);

}
