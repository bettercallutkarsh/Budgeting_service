package com.budgetingapp.repository;

import com.budgetingapp.entity.MoneyEntry;
import com.budgetingapp.entity.enums.BudgetBucket;
import com.budgetingapp.entity.enums.EntryStatus;
import com.budgetingapp.entity.enums.EntryType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MoneyEntrySpecifications {

    private MoneyEntrySpecifications() {
    }

    public static Specification<MoneyEntry> search(Long userId, Instant startUtc, Instant endExclusiveUtc,
                                                   EntryType entryType, BudgetBucket bucket,
                                                   String categoryCode, EntryStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("userId"), userId));
            if (startUtc != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("entryAtUtc"), startUtc));
            }
            if (endExclusiveUtc != null) {
                predicates.add(cb.lessThan(root.get("entryAtUtc"), endExclusiveUtc));
            }
            if (entryType != null) {
                predicates.add(cb.equal(root.get("entryType"), entryType));
            }
            if (bucket != null) {
                predicates.add(cb.equal(root.get("bucket"), bucket));
            }
            if (categoryCode != null) {
                predicates.add(cb.equal(root.get("categoryCode"), categoryCode));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
