package com.budgetingapp.repository;

import com.budgetingapp.entity.MoneyEntry;
import com.budgetingapp.entity.enums.BudgetBucket;
import com.budgetingapp.entity.enums.EntryStatus;
import com.budgetingapp.entity.enums.EntryType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class MoneyEntrySpecificationsTest {

    private static final Long USER_ID = 7L;

    private Root<MoneyEntry> root;
    private CriteriaQuery<MoneyEntry> query;
    private CriteriaBuilder cb;

    private Path<Object> userPath;
    private Path<Object> userIdPath;
    private Path<Instant> entryAtUtcPath;
    private Path<Object> entryTypePath;
    private Path<Object> bucketPath;
    private Path<Object> categoryCodePath;
    private Path<Object> statusPath;

    private Predicate userPredicate;
    private Predicate startPredicate;
    private Predicate endPredicate;
    private Predicate typePredicate;
    private Predicate bucketPredicate;
    private Predicate categoryPredicate;
    private Predicate statusPredicate;

    @BeforeEach
    void setUp() {
        root = mock(Root.class);
        query = mock(CriteriaQuery.class);
        cb = mock(CriteriaBuilder.class);
        userPath = mock(Path.class);
        userIdPath = mock(Path.class);
        entryAtUtcPath = mock(Path.class);
        entryTypePath = mock(Path.class);
        bucketPath = mock(Path.class);
        categoryCodePath = mock(Path.class);
        statusPath = mock(Path.class);
        userPredicate = mock(Predicate.class);
        startPredicate = mock(Predicate.class);
        endPredicate = mock(Predicate.class);
        typePredicate = mock(Predicate.class);
        bucketPredicate = mock(Predicate.class);
        categoryPredicate = mock(Predicate.class);
        statusPredicate = mock(Predicate.class);
        when(root.get("user")).thenReturn(userPath);
        when(userPath.get("userId")).thenReturn(userIdPath);
        doReturn(userPredicate).when(cb).equal(userIdPath, USER_ID);
    }

    @Test
    void alwaysFiltersByUserEvenWithoutAnyOptionalFilter() {
        Specification<MoneyEntry> specification =
                MoneyEntrySpecifications.search(USER_ID, null, null, null, null, null, null);

        specification.toPredicate(root, query, cb);

        assertAndInvokedWith(userPredicate);
        verify(cb).equal(userIdPath, USER_ID);
    }

    @Test
    void fullFilterProducesSevenPredicatesInOrder() {
        Instant startUtc = Instant.parse("2026-10-01T00:00:00Z");
        Instant endUtc = Instant.parse("2026-11-01T00:00:00Z");
        when(root.<Instant>get("entryAtUtc")).thenReturn(entryAtUtcPath);
        when(root.get("entryType")).thenReturn(entryTypePath);
        when(root.get("bucket")).thenReturn(bucketPath);
        when(root.get("categoryCode")).thenReturn(categoryCodePath);
        when(root.get("status")).thenReturn(statusPath);
        doReturn(startPredicate).when(cb).greaterThanOrEqualTo(entryAtUtcPath, startUtc);
        doReturn(endPredicate).when(cb).lessThan(entryAtUtcPath, endUtc);
        doReturn(typePredicate).when(cb).equal(entryTypePath, EntryType.EXPENSE);
        doReturn(bucketPredicate).when(cb).equal(bucketPath, BudgetBucket.ESSENTIALS);
        doReturn(categoryPredicate).when(cb).equal(categoryCodePath, "GROCERIES");
        doReturn(statusPredicate).when(cb).equal(statusPath, EntryStatus.POSTED);
        Specification<MoneyEntry> specification = MoneyEntrySpecifications.search(
                USER_ID, startUtc, endUtc, EntryType.EXPENSE, BudgetBucket.ESSENTIALS, "GROCERIES",
                EntryStatus.POSTED);

        specification.toPredicate(root, query, cb);

        assertAndInvokedWith(userPredicate, startPredicate, endPredicate, typePredicate, bucketPredicate,
                categoryPredicate, statusPredicate);
        verify(cb).equal(userIdPath, USER_ID);
        verify(cb).greaterThanOrEqualTo(entryAtUtcPath, startUtc);
        verify(cb).lessThan(entryAtUtcPath, endUtc);
        verify(cb).equal(entryTypePath, EntryType.EXPENSE);
        verify(cb).equal(bucketPath, BudgetBucket.ESSENTIALS);
        verify(cb).equal(categoryCodePath, "GROCERIES");
        verify(cb).equal(statusPath, EntryStatus.POSTED);
    }

    @Test
    void closedDateRangeProducesThreePredicates() {
        Instant startUtc = Instant.parse("2026-10-01T00:00:00Z");
        Instant endUtc = Instant.parse("2026-11-01T00:00:00Z");
        when(root.<Instant>get("entryAtUtc")).thenReturn(entryAtUtcPath);
        doReturn(startPredicate).when(cb).greaterThanOrEqualTo(entryAtUtcPath, startUtc);
        doReturn(endPredicate).when(cb).lessThan(entryAtUtcPath, endUtc);
        Specification<MoneyEntry> specification =
                MoneyEntrySpecifications.search(USER_ID, startUtc, endUtc, null, null, null, null);

        specification.toPredicate(root, query, cb);

        assertAndInvokedWith(userPredicate, startPredicate, endPredicate);
        verify(cb).greaterThanOrEqualTo(entryAtUtcPath, startUtc);
        verify(cb).lessThan(entryAtUtcPath, endUtc);
    }

    @Test
    void openEndedRangeProducesTwoPredicatesWithoutUpperBound() {
        Instant startUtc = Instant.parse("2026-10-01T00:00:00Z");
        when(root.<Instant>get("entryAtUtc")).thenReturn(entryAtUtcPath);
        doReturn(startPredicate).when(cb).greaterThanOrEqualTo(entryAtUtcPath, startUtc);
        Specification<MoneyEntry> specification =
                MoneyEntrySpecifications.search(USER_ID, startUtc, null, null, null, null, null);

        specification.toPredicate(root, query, cb);

        assertAndInvokedWith(userPredicate, startPredicate);
        verify(cb).greaterThanOrEqualTo(entryAtUtcPath, startUtc);
    }

    @Test
    void categoryAndStatusFiltersProduceThreePredicatesWithoutRange() {
        when(root.get("categoryCode")).thenReturn(categoryCodePath);
        when(root.get("status")).thenReturn(statusPath);
        doReturn(categoryPredicate).when(cb).equal(categoryCodePath, "SIP");
        doReturn(statusPredicate).when(cb).equal(statusPath, EntryStatus.VOID);
        Specification<MoneyEntry> specification = MoneyEntrySpecifications.search(
                USER_ID, null, null, null, null, "SIP", EntryStatus.VOID);

        specification.toPredicate(root, query, cb);

        assertAndInvokedWith(userPredicate, categoryPredicate, statusPredicate);
        verify(cb).equal(categoryCodePath, "SIP");
        verify(cb).equal(statusPath, EntryStatus.VOID);
    }

    private void assertAndInvokedWith(Predicate... expected) {
        Object[] actual = andArguments();
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertSame(expected[i], actual[i]);
        }
    }

    private Object[] andArguments() {
        for (Invocation invocation : mockingDetails(cb).getInvocations()) {
            if (!invocation.getMethod().getName().equals("and")) {
                continue;
            }
            Object[] rawArguments = invocation.getRawArguments();
            if (rawArguments.length == 1 && rawArguments[0] instanceof Predicate[] predicates) {
                return predicates;
            }
            return rawArguments;
        }
        throw new AssertionError("criteriaBuilder.and(...) was never invoked");
    }
}
