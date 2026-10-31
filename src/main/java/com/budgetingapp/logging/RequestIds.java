package com.budgetingapp.logging;

import org.slf4j.MDC;

import java.util.UUID;

public final class RequestIds {

    public static final String MDC_KEY = "requestId";

    private RequestIds() {
    }

    public static String newId() {
        return "req-" + UUID.randomUUID().toString().replace("-", "");
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String currentOrNew() {
        String current = current();
        return current == null || current.isBlank() ? newId() : current;
    }

    public static void set(String requestId) {
        MDC.put(MDC_KEY, requestId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
