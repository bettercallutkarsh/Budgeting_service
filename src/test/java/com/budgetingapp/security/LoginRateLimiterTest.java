package com.budgetingapp.security;

import com.budgetingapp.exception.LoginRateLimitedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginRateLimiterTest {

    private static final Instant BASE = Instant.parse("2026-10-18T12:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final MutableClock clock = new MutableClock(BASE, ZoneId.of("UTC"));

    @Test
    void allowsAttemptsBelowTheConfiguredThreshold() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, WINDOW, clock);
        limiter.recordFailure("1.1.1.1");
        limiter.recordFailure("1.1.1.1");

        assertDoesNotThrow(() -> limiter.checkAllowed("1.1.1.1"));
        assertEquals(2, limiter.failureCount("1.1.1.1"));
    }

    @Test
    void unknownClientIsAllowedWithoutState() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, WINDOW, clock);

        assertDoesNotThrow(() -> limiter.checkAllowed("203.0.113.7"));
        assertEquals(0, limiter.trackedIpCount());
    }

    @Test
    void blocksAtExactThresholdWithFullWindowRetryAfter() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, WINDOW, clock);
        limiter.recordFailure("1.1.1.1");
        limiter.recordFailure("1.1.1.1");
        limiter.recordFailure("1.1.1.1");

        LoginRateLimitedException exception = assertThrows(LoginRateLimitedException.class,
                () -> limiter.checkAllowed("1.1.1.1"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
        assertEquals("LOGIN_RATE_LIMITED", exception.getCode());
        assertEquals(900, exception.getRetryAfterSeconds());

        LoginRateLimitedException repeated = assertThrows(LoginRateLimitedException.class,
                () -> limiter.checkAllowed("1.1.1.1"));
        assertEquals(900, repeated.getRetryAfterSeconds());
    }

    @Test
    void retryAfterShrinksAsTheWindowElapses() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, WINDOW, clock);
        limiter.recordFailure("1.1.1.1");
        limiter.recordFailure("1.1.1.1");
        limiter.recordFailure("1.1.1.1");

        clock.advance(Duration.ofMinutes(5));
        assertEquals(600, assertThrows(LoginRateLimitedException.class,
                () -> limiter.checkAllowed("1.1.1.1")).getRetryAfterSeconds());

        clock.advance(Duration.ofMinutes(9));
        assertEquals(60, assertThrows(LoginRateLimitedException.class,
                () -> limiter.checkAllowed("1.1.1.1")).getRetryAfterSeconds());

        clock.advance(Duration.ofSeconds(30));
        assertEquals(30, assertThrows(LoginRateLimitedException.class,
                () -> limiter.checkAllowed("1.1.1.1")).getRetryAfterSeconds());
    }

    @Test
    void windowExpiryUnblocksClientAndClearsState() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, WINDOW, clock);
        limiter.recordFailure("1.1.1.1");
        limiter.recordFailure("1.1.1.1");
        limiter.recordFailure("1.1.1.1");

        clock.advance(WINDOW);

        assertDoesNotThrow(() -> limiter.checkAllowed("1.1.1.1"));
        assertEquals(0, limiter.failureCount("1.1.1.1"));
        assertEquals(0, limiter.trackedIpCount());
    }

    @Test
    void rollingWindowKeepsOnlyFailuresInsideTheWindow() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, WINDOW, clock);
        limiter.recordFailure("1.1.1.1");
        clock.advance(Duration.ofMinutes(10));
        limiter.recordFailure("1.1.1.1");
        clock.advance(Duration.ofMinutes(3));
        limiter.recordFailure("1.1.1.1");
        clock.advance(Duration.ofMinutes(2));

        assertDoesNotThrow(() -> limiter.checkAllowed("1.1.1.1"));
        assertEquals(2, limiter.failureCount("1.1.1.1"));

        limiter.recordFailure("1.1.1.1");
        assertThrows(LoginRateLimitedException.class, () -> limiter.checkAllowed("1.1.1.1"));
    }

    @Test
    void clientBudgetsAreIndependentPerIp() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, WINDOW, clock);
        limiter.recordFailure("1.1.1.1");
        limiter.recordFailure("1.1.1.1");
        limiter.recordFailure("1.1.1.1");

        assertThrows(LoginRateLimitedException.class, () -> limiter.checkAllowed("1.1.1.1"));
        assertDoesNotThrow(() -> limiter.checkAllowed("2.2.2.2"));
        assertDoesNotThrow(() -> limiter.checkAllowed("3.3.3.3"));
    }

    @Test
    void failureCountNeverGrowsBeyondMaxAttempts() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, WINDOW, clock);
        for (int i = 0; i < 10; i++) {
            limiter.recordFailure("1.1.1.1");
        }

        assertEquals(3, limiter.failureCount("1.1.1.1"));
    }

    @Test
    void rejectsInvalidConstructorArguments() {
        assertThrows(IllegalArgumentException.class, () -> new LoginRateLimiter(0, WINDOW, clock));
        assertThrows(IllegalArgumentException.class,
                () -> new LoginRateLimiter(3, Duration.ZERO, clock));
        assertThrows(IllegalArgumentException.class,
                () -> new LoginRateLimiter(3, Duration.ofMinutes(-5), clock));
        assertThrows(IllegalArgumentException.class,
                () -> new LoginRateLimiter(3, WINDOW, null));
    }

    @Test
    void concurrentRecordFailureCallsNeverLoseUpdates() throws Exception {
        LoginRateLimiter limiter = new LoginRateLimiter(10_000, WINDOW, clock);
        int threads = 8;
        int iterationsPerThread = 250;
        runConcurrently(threads, () -> {
            for (int i = 0; i < iterationsPerThread; i++) {
                limiter.recordFailure("10.0.0.1");
            }
        });

        assertEquals(threads * iterationsPerThread, limiter.failureCount("10.0.0.1"));
    }

    @Test
    void concurrentStormCannotBypassTheThreshold() throws Exception {
        LoginRateLimiter limiter = new LoginRateLimiter(500, WINDOW, clock);
        int threads = 8;
        int iterationsPerThread = 100;
        runConcurrently(threads, () -> {
            for (int i = 0; i < iterationsPerThread; i++) {
                limiter.recordFailure("10.0.0.2");
            }
        });
        assertEquals(500, limiter.failureCount("10.0.0.2"));

        runConcurrently(threads, () -> {
            for (int i = 0; i < iterationsPerThread; i++) {
                limiter.recordFailure("10.0.0.2");
                assertThrows(LoginRateLimitedException.class, () -> limiter.checkAllowed("10.0.0.2"),
                        "a client at the threshold must stay blocked under concurrent load");
            }
        });

        assertEquals(500, limiter.failureCount("10.0.0.2"));
        assertThrows(LoginRateLimitedException.class, () -> limiter.checkAllowed("10.0.0.2"));
    }

    private void runConcurrently(int threads, CheckedRunnable action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    action.run();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
