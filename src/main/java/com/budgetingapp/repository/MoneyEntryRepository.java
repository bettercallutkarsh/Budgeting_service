package com.budgetingapp.repository;

import com.budgetingapp.entity.MoneyEntry;
import com.budgetingapp.entity.enums.BudgetBucket;
import com.budgetingapp.entity.enums.EntryStatus;
import com.budgetingapp.entity.enums.EntryType;
import com.budgetingapp.entity.enums.PaymentMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MoneyEntryRepository extends JpaRepository<MoneyEntry, Long>, JpaSpecificationExecutor<MoneyEntry> {

    Page<MoneyEntry> findByUser_UserIdOrderByEntryAtUtcDescMoneyEntryIdDesc(Long userId, Pageable pageable);

    Page<MoneyEntry> findByUser_UserIdAndEntryAtUtcGreaterThanEqualAndEntryAtUtcLessThanOrderByEntryAtUtcDescMoneyEntryIdDesc(
            Long userId, Instant startUtc, Instant endExclusiveUtc, Pageable pageable);

    Page<MoneyEntry> findByUser_UserIdAndEntryTypeAndEntryAtUtcGreaterThanEqualAndEntryAtUtcLessThanOrderByEntryAtUtcDescMoneyEntryIdDesc(
            Long userId, EntryType entryType, Instant startUtc, Instant endExclusiveUtc, Pageable pageable);

    Page<MoneyEntry> findByUser_UserIdAndBucketAndEntryAtUtcGreaterThanEqualAndEntryAtUtcLessThanOrderByEntryAtUtcDescMoneyEntryIdDesc(
            Long userId, BudgetBucket bucket, Instant startUtc, Instant endExclusiveUtc, Pageable pageable);

    Page<MoneyEntry> findByUser_UserIdAndEntryTypeAndBucketAndEntryAtUtcGreaterThanEqualAndEntryAtUtcLessThanOrderByEntryAtUtcDescMoneyEntryIdDesc(
            Long userId, EntryType entryType, BudgetBucket bucket, Instant startUtc, Instant endExclusiveUtc, Pageable pageable);

    boolean existsByUser_UserIdAndEntryTypeAndEntryAtUtcGreaterThanEqualAndEntryAtUtcLessThan(
            Long userId, EntryType entryType, Instant startUtc, Instant endExclusiveUtc);

    boolean existsByUser_UserIdAndClientRequestId(Long userId, String clientRequestId);

    Optional<MoneyEntry> findByMoneyEntryIdAndUser_UserId(Long moneyEntryId, Long userId);

    Optional<MoneyEntry> findByUser_UserIdAndClientRequestId(Long userId, String clientRequestId);

    boolean existsByUser_UserIdAndEntryTypeAndPaymentMethodAndStatusAndEntryAtUtcGreaterThanEqual(
            Long userId, EntryType entryType, PaymentMethod paymentMethod, EntryStatus status, Instant startUtc);

    boolean existsByUser_UserIdAndEntryTypeAndStatusAndEntryAtUtcGreaterThanEqual(
            Long userId, EntryType entryType, EntryStatus status, Instant startUtc);

    @Query("select coalesce(sum(m.amount), 0) from MoneyEntry m where m.user.userId = :userId "
            + "and m.entryType = :entryType and m.paymentMethod = :paymentMethod and m.status = :status "
            + "and m.entryAtUtc >= :startUtc")
    BigDecimal sumAmountByUser_UserIdAndEntryTypeAndPaymentMethodAndStatusAndEntryAtUtcGreaterThanEqual(
            @Param("userId") Long userId, @Param("entryType") EntryType entryType,
            @Param("paymentMethod") PaymentMethod paymentMethod, @Param("status") EntryStatus status,
            @Param("startUtc") Instant startUtc);

    @Query("select coalesce(sum(m.amount), 0) from MoneyEntry m where m.user.userId = :userId "
            + "and m.entryType = :entryType and m.status = :status and m.entryAtUtc >= :startUtc")
    BigDecimal sumAmountByUser_UserIdAndEntryTypeAndStatusAndEntryAtUtcGreaterThanEqual(
            @Param("userId") Long userId, @Param("entryType") EntryType entryType,
            @Param("status") EntryStatus status, @Param("startUtc") Instant startUtc);

    @Query(value = """
            SELECT DATE(CONVERT_TZ(m.entry_at_utc, '+00:00', '+05:30')) AS ist_day,
                   m.entry_type AS entry_type,
                   m.bucket AS bucket,
                   COALESCE(SUM(m.amount), 0) AS total
            FROM money_entries m
            WHERE m.user_id = :userId
              AND m.status = 'POSTED'
              AND m.entry_at_utc >= :startUtc
              AND m.entry_at_utc < :endExclusiveUtc
            GROUP BY DATE(CONVERT_TZ(m.entry_at_utc, '+00:00', '+05:30')), m.entry_type, m.bucket
            """, nativeQuery = true)
    List<PostedEntryDayTotal> sumPostedTotalsByIstDay(@Param("userId") Long userId,
                                                      @Param("startUtc") Instant startUtc,
                                                      @Param("endExclusiveUtc") Instant endExclusiveUtc);

    interface PostedEntryDayTotal {

        java.time.LocalDate getIstDay();

        String getEntryType();

        String getBucket();

        java.math.BigDecimal getTotal();
    }

}
