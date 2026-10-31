package com.budgetingapp.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSanitizerTest {

    @Test
    void returnsPlaceholderForNullAndEmptyValues() {
        assertEquals("-", LogSanitizer.sanitize(null));
        assertEquals("-", LogSanitizer.sanitize(""));
    }

    @Test
    void replacesLineBreaksTabsAndControlCharacters() {
        String sanitized = LogSanitizer.sanitize("value\r\ninjected\tvalue\u0000end");
        assertFalse(sanitized.contains("\r"));
        assertFalse(sanitized.contains("\n"));
        assertFalse(sanitized.contains("\t"));
        assertFalse(sanitized.contains("\u0000"));
        assertEquals("value__injected_value_end", sanitized);
    }

    @Test
    void keepsNormalRouteValuesUnchanged() {
        assertEquals("/api/v1/budgets/{month}", LogSanitizer.sanitize("/api/v1/budgets/{month}"));
    }

    @Test
    void truncatesOverlongValues() {
        String sanitized = LogSanitizer.sanitize("a".repeat(500));
        assertTrue(sanitized.length() <= 203);
        assertTrue(sanitized.endsWith("..."));
    }
}
