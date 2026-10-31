package com.budgetingapp.logging;

public final class LogSanitizer {

    private static final int MAX_LENGTH = 200;

    private LogSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return "-";
        }
        StringBuilder sanitized = new StringBuilder(Math.min(value.length(), MAX_LENGTH));
        for (int i = 0; i < value.length() && sanitized.length() < MAX_LENGTH; i++) {
            char character = value.charAt(i);
            if (character == '\r' || character == '\n' || character == '\t' || Character.isISOControl(character)) {
                sanitized.append('_');
            } else {
                sanitized.append(character);
            }
        }
        if (value.length() > MAX_LENGTH) {
            sanitized.append("...");
        }
        return sanitized.toString();
    }
}
