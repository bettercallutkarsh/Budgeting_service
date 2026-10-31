package com.budgetingapp.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.budgetingapp.exception.BusinessException;
import com.budgetingapp.exception.GlobalExceptionHandler;
import com.budgetingapp.exception.LoginRateLimitedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequestLoggingIntegrationTest {

    private static final String REQUEST_ID_PATTERN = "req-[0-9a-f]{32}";

    private final Logger requestLogger = (Logger) LoggerFactory.getLogger(RequestCorrelationFilter.REQUEST_LOGGER_NAME);
    private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

    private MockMvc mockMvc;
    private ListAppender<ILoggingEvent> requestAppender;
    private ListAppender<ILoggingEvent> handlerAppender;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler(Clock.systemUTC()))
                .addFilters(new RequestCorrelationFilter("budgeting-backend", "test", 1000))
                .build();
        requestAppender = attach(requestLogger);
        handlerAppender = attach(handlerLogger);
    }

    @AfterEach
    void tearDown() {
        detach(requestLogger, requestAppender);
        detach(handlerLogger, handlerAppender);
        RequestIds.clear();
        MDC.clear();
    }

    @Test
    void successfulRequestExposesCorrelationIdAndLogsSummaryOnce() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/ok"))
                .andExpect(status().isOk())
                .andReturn();

        String requestId = result.getResponse().getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        assertNotNull(requestId);
        assertTrue(requestId.matches(REQUEST_ID_PATTERN));
        assertEquals(1, requestAppender.list.stream()
                .filter(event -> event.getLevel() == Level.INFO
                        && event.getFormattedMessage().startsWith("request.completed"))
                .count());
        String message = requestAppender.list.stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .findFirst().orElseThrow().getFormattedMessage();
        assertTrue(message.contains("method=GET"));
        assertTrue(message.contains("route=/test/ok"));
        assertTrue(message.contains("status=200"));
        assertTrue(message.matches(".*durationMs=\\d+.*"));
    }

    @Test
    void controllerSeesSameCorrelationIdAndMdcIsClearedAfterwards() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/mdc"))
                .andExpect(status().isOk())
                .andReturn();

        String requestId = result.getResponse().getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        assertEquals(requestId, result.getResponse().getContentAsString());
        assertNull(MDC.get(RequestIds.MDC_KEY));
    }

    @Test
    void summaryUsesRouteTemplateInsteadOfRawPath() throws Exception {
        mockMvc.perform(get("/test/items/123"))
                .andExpect(status().isOk());

        String message = requestAppender.list.get(requestAppender.list.size() - 1).getFormattedMessage();
        assertTrue(message.contains("route=/test/items/{id}"));
        assertFalse(message.contains("route=/test/items/123"));
    }

    @Test
    void unexpectedExceptionReturnsSafe500AndSingleErrorLogWithMatchingTraceId() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn();

        String requestId = result.getResponse().getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        String body = result.getResponse().getContentAsString();
        assertTrue(requestId.matches(REQUEST_ID_PATTERN));
        assertTrue(body.contains("\"traceId\":\"" + requestId + "\""));
        assertFalse(body.contains("super-secret"));
        assertFalse(body.contains("IllegalStateException"));

        var errorEvents = handlerAppender.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .toList();
        assertEquals(1, errorEvents.size());
        ILoggingEvent errorEvent = errorEvents.get(0);
        assertTrue(errorEvent.getFormattedMessage().startsWith("request.failed"));
        assertTrue(errorEvent.getFormattedMessage().contains("exceptionClass=java.lang.IllegalStateException"));
        assertNotNull(errorEvent.getThrowableProxy());
        assertEquals(requestId, errorEvent.getMDCPropertyMap().get(RequestIds.MDC_KEY));
    }

    @Test
    void databaseFailureIsLoggedWithSafeMetadataAndGenericResponse() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/db-failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("secret"));
        assertFalse(body.contains("SQLException"));

        var errorEvents = handlerAppender.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .toList();
        assertEquals(1, errorEvents.size());
        String message = errorEvents.get(0).getFormattedMessage();
        assertTrue(message.startsWith("db.failure"));
        assertTrue(message.contains("sqlState=08001"));
        assertTrue(message.contains("vendorCode=1040"));
        assertNotNull(errorEvents.get(0).getThrowableProxy());
    }

    @Test
    void businessConflictIsWarnWithoutErrorStack() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEST_CONFLICT"))
                .andReturn();

        assertTrue(handlerAppender.list.stream().noneMatch(event -> event.getLevel() == Level.ERROR));
        assertTrue(handlerAppender.list.stream().anyMatch(event -> event.getLevel() == Level.WARN
                && event.getFormattedMessage().contains("errorCode=TEST_CONFLICT")));
        String requestId = result.getResponse().getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        assertTrue(result.getResponse().getContentAsString().contains("\"traceId\":\"" + requestId + "\""));
    }

    @Test
    void invalidParameterIsNotLoggedAsError() throws Exception {
        mockMvc.perform(get("/test/param").param("value", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));

        assertTrue(handlerAppender.list.stream().noneMatch(event -> event.getLevel() == Level.ERROR));
        assertTrue(handlerAppender.list.stream().anyMatch(event -> event.getLevel() == Level.INFO
                && event.getFormattedMessage().startsWith("request.invalid_parameter")));
    }

    @Test
    void rateLimitIsWarnWithoutErrorStack() throws Exception {
        mockMvc.perform(get("/test/rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));

        assertTrue(handlerAppender.list.stream().noneMatch(event -> event.getLevel() == Level.ERROR));
        assertTrue(handlerAppender.list.stream().anyMatch(event -> event.getLevel() == Level.WARN
                && event.getFormattedMessage().startsWith("security.rate_limited")));
    }

    @Test
    void requestBodiesAreNeverLogged() throws Exception {
        ListAppender<ILoggingEvent> rootAppender = attach(rootLogger);
        try {
            mockMvc.perform(post("/test/echo")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("password=super-secret-value"))
                    .andExpect(status().isOk());
        } finally {
            detach(rootLogger, rootAppender);
        }

        assertTrue(rootAppender.list.stream()
                .noneMatch(event -> event.getFormattedMessage().contains("super-secret-value")));
    }

    @Test
    void distinctRequestsReceiveDistinctCorrelationIds() throws Exception {
        String first = mockMvc.perform(get("/test/ok")).andReturn()
                .getResponse().getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        String second = mockMvc.perform(get("/test/ok")).andReturn()
                .getResponse().getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);

        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first, second);
    }

    private ListAppender<ILoggingEvent> attach(Logger logger) {
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        return listAppender;
    }

    private void detach(Logger logger, ListAppender<ILoggingEvent> listAppender) {
        if (listAppender != null) {
            logger.detachAppender(listAppender);
            listAppender.stop();
        }
    }

    @RestController
    static class TestController {

        @GetMapping("/test/ok")
        String ok() {
            return "ok";
        }

        @GetMapping("/test/mdc")
        String mdc() {
            return RequestIds.current();
        }

        @GetMapping("/test/items/{id}")
        String item(@PathVariable("id") String id) {
            return id;
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("database password=super-secret");
        }

        @GetMapping("/test/conflict")
        String conflict() {
            throw new BusinessException(HttpStatus.CONFLICT, "TEST_CONFLICT", "conflict");
        }

        @GetMapping("/test/param")
        String param(@RequestParam("value") int value) {
            return String.valueOf(value);
        }

        @GetMapping("/test/rate-limit")
        String rateLimit() {
            throw new LoginRateLimitedException(30);
        }

        @GetMapping("/test/db-failure")
        String dbFailure() {
            throw new DataAccessResourceFailureException("secret database detail",
                    new SQLException("secret sql", "08001", 1040));
        }

        @PostMapping(value = "/test/echo", consumes = MediaType.TEXT_PLAIN_VALUE)
        String echo(@RequestBody(required = false) String body) {
            return "ok";
        }
    }
}
