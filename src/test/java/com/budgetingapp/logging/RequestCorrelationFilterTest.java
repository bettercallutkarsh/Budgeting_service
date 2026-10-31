package com.budgetingapp.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestCorrelationFilterTest {

    private static final String REQUEST_ID_PATTERN = "req-[0-9a-f]{32}";

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter("budgeting-backend", "test", 1000);
    private final Logger requestLogger = (Logger) LoggerFactory.getLogger(RequestCorrelationFilter.REQUEST_LOGGER_NAME);
    private ListAppender<ILoggingEvent> appender;

    @AfterEach
    void tearDown() {
        RequestIds.clear();
        if (appender != null) {
            requestLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void addsServerGeneratedCorrelationIdToMdcAndResponseHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/budgets/2026-10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringRequest = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                mdcDuringRequest.set(MDC.get(RequestIds.MDC_KEY)));

        String header = response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        assertNotNull(header);
        assertTrue(header.matches(REQUEST_ID_PATTERN));
        assertEquals(header, mdcDuringRequest.get());
        assertNull(MDC.get(RequestIds.MDC_KEY));
    }

    @Test
    void ignoresCallerSuppliedRequestIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/budgets/2026-10");
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "caller-controlled\ninjected");
        MockHttpServletResponse response = new MockHttpServletResponse();
        appender = attachRequestAppender();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        String header = response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        assertNotNull(header);
        assertTrue(header.matches(REQUEST_ID_PATTERN));
        assertNotEquals("caller-controlled\ninjected", header);
        assertEquals(1, appender.list.size());
        assertFalse(appender.list.get(0).getFormattedMessage().contains("injected"));
    }

    @Test
    void clearsMdcWhenDownstreamThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/budgets/2026-10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(ServletException.class, () -> filter.doFilter(request, response,
                (servletRequest, servletResponse) -> {
                    throw new ServletException("boom");
                }));

        assertNotNull(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER));
        assertNull(MDC.get(RequestIds.MDC_KEY));
    }

    @Test
    void sequentialRequestsOnSameThreadReceiveDistinctIdsAndDoNotInherit() throws Exception {
        AtomicReference<String> firstDuringRequest = new AtomicReference<>();
        AtomicReference<String> secondDuringRequest = new AtomicReference<>();

        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/budgets/2026-10"), firstResponse,
                (servletRequest, servletResponse) -> firstDuringRequest.set(MDC.get(RequestIds.MDC_KEY)));
        assertNull(MDC.get(RequestIds.MDC_KEY));

        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/money-entries"), secondResponse,
                (servletRequest, servletResponse) -> secondDuringRequest.set(MDC.get(RequestIds.MDC_KEY)));
        assertNull(MDC.get(RequestIds.MDC_KEY));

        assertNotNull(firstDuringRequest.get());
        assertNotNull(secondDuringRequest.get());
        assertNotEquals(firstDuringRequest.get(), secondDuringRequest.get());
    }

    @Test
    void emitsSingleRequestSummaryWithSafeRouteTemplateAndTiming() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/budgets/2026-10");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/budgets/{month}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        appender = attachRequestAppender();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(200));

        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.INFO, event.getLevel());
        String message = event.getFormattedMessage();
        assertTrue(message.startsWith("request.completed"));
        assertTrue(message.contains("method=GET"));
        assertTrue(message.contains("route=/api/v1/budgets/{month}"));
        assertTrue(message.contains("status=200"));
        assertTrue(message.matches(".*durationMs=\\d+.*"));
        assertFalse(message.contains("2026-10"));
        assertEquals(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER),
                event.getMDCPropertyMap().get(RequestIds.MDC_KEY));
    }

    @Test
    void excludedStaticRequestsAreLoggedAtDebugInsteadOfInfo() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();
        appender = attachRequestAppender();
        Level previousLevel = requestLogger.getLevel();
        requestLogger.setLevel(Level.DEBUG);

        try {
            filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            });
        } finally {
            requestLogger.setLevel(previousLevel);
        }

        assertEquals(1, appender.list.size());
        assertEquals(Level.DEBUG, appender.list.get(0).getLevel());
    }

    @Test
    void slowRequestsEmitWarnEvent() throws Exception {
        RequestCorrelationFilter slowFilter = new RequestCorrelationFilter("budgeting-backend", "test", 1);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reports/summary");
        MockHttpServletResponse response = new MockHttpServletResponse();
        appender = attachRequestAppender();

        slowFilter.doFilter(request, response, (servletRequest, servletResponse) -> {
            try {
                Thread.sleep(15);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == Level.WARN
                && event.getFormattedMessage().startsWith("request.slow")));
    }

    @Test
    void userControlledNewlinesCannotInjectAdditionalLogRecords() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/v1/budgets/2026-10\nFAKE request.completed status=200");
        MockHttpServletResponse response = new MockHttpServletResponse();
        appender = attachRequestAppender();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        assertEquals(1, appender.list.size());
        String message = appender.list.get(0).getFormattedMessage();
        assertFalse(message.contains("\n"));
        assertFalse(message.contains("\r"));
    }

    private ListAppender<ILoggingEvent> attachRequestAppender() {
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        requestLogger.addAppender(listAppender);
        return listAppender;
    }
}
