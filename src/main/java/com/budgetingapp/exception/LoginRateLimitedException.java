package com.budgetingapp.exception;

import org.springframework.http.HttpStatus;

public class LoginRateLimitedException extends BusinessException {

    private final long retryAfterSeconds;

    public LoginRateLimitedException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED",
                "Too many failed login attempts. Please try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
