package com.budgetingapp.security;

import com.budgetingapp.exception.LoginRateLimitedException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class LoginRateLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, ArrayDeque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginRateLimiter(int maxAttempts, Duration window, Clock clock) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock is required");
        }
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    public void checkAllowed(String clientIp) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        sweep(cutoff);
        AtomicLong retryAfterSeconds = new AtomicLong();
        failures.computeIfPresent(clientIp, (ip, timestamps) -> {
            prune(timestamps, cutoff);
            if (timestamps.size() >= maxAttempts) {
                retryAfterSeconds.set(secondsUntil(timestamps.peekFirst().plus(window), now));
            }
            return timestamps.isEmpty() ? null : timestamps;
        });
        long retryAfter = retryAfterSeconds.get();
        if (retryAfter > 0) {
            throw new LoginRateLimitedException(retryAfter);
        }
    }

    public void recordFailure(String clientIp) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        sweep(cutoff);
        failures.compute(clientIp, (ip, timestamps) -> {
            ArrayDeque<Instant> entry = timestamps == null ? new ArrayDeque<>() : timestamps;
            prune(entry, cutoff);
            entry.addLast(now);
            while (entry.size() > maxAttempts) {
                entry.removeFirst();
            }
            return entry;
        });
    }

    int failureCount(String clientIp) {
        int[] count = new int[1];
        failures.computeIfPresent(clientIp, (ip, timestamps) -> {
            count[0] = timestamps.size();
            return timestamps;
        });
        return count[0];
    }

    int trackedIpCount() {
        return failures.size();
    }

    private void sweep(Instant cutoff) {
        for (String ip : failures.keySet()) {
            failures.computeIfPresent(ip, (key, timestamps) -> {
                prune(timestamps, cutoff);
                return timestamps.isEmpty() ? null : timestamps;
            });
        }
    }

    private void prune(ArrayDeque<Instant> timestamps, Instant cutoff) {
        while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(cutoff)) {
            timestamps.removeFirst();
        }
    }

    private long secondsUntil(Instant target, Instant now) {
        long millis = target.toEpochMilli() - now.toEpochMilli();
        long seconds = (millis + 999) / 1000;
        return Math.max(1, seconds);
    }
}
