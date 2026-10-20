package com.budgetingapp.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

public class MutableClock extends Clock {

    private volatile Instant current;
    private final ZoneId zone;

    public MutableClock(Instant initial, ZoneId zone) {
        this.current = initial;
        this.zone = zone;
    }

    public void advance(Duration duration) {
        current = current.plus(duration);
    }

    public void set(Instant instant) {
        current = instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(current, zone);
    }

    @Override
    public Instant instant() {
        return current;
    }
}
