package com.budgetingapp.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.budgetingapp.security.SecurityErrorWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RequestCorrelationSecurityIntegrationTest {

    private static final String REQUEST_ID_PATTERN = "req-[0-9a-f]{32}";

    @Autowired
    private MockMvc mockMvc;

    private final Logger securityLogger = (Logger) LoggerFactory.getLogger(SecurityErrorWriter.class);
    private ListAppender<ILoggingEvent> securityAppender;

    @BeforeEach
    void setUp() {
        securityAppender = new ListAppender<>();
        securityAppender.start();
        securityLogger.addAppender(securityAppender);
    }

    @AfterEach
    void tearDown() {
        securityLogger.detachAppender(securityAppender);
        securityAppender.stop();
        MDC.clear();
    }

    @Test
    void anonymousRequestGetsCorrelationHeaderAndMatchingErrorTraceId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/budgets/2026-10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn();

        String requestId = result.getResponse().getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        assertNotNull(requestId);
        assertTrue(requestId.matches(REQUEST_ID_PATTERN));
        assertTrue(result.getResponse().getContentAsString().contains("\"traceId\":\"" + requestId + "\""));
        assertTrue(securityAppender.list.stream().anyMatch(event -> event.getLevel() == Level.INFO
                && event.getFormattedMessage().startsWith("security.authentication_required")
                && requestId.equals(event.getMDCPropertyMap().get(RequestIds.MDC_KEY))));
        assertTrue(securityAppender.list.stream().noneMatch(event -> event.getLevel() == Level.ERROR));
        assertNull(MDC.get(RequestIds.MDC_KEY));
    }

    @Test
    void csrfRejectionGetsCorrelationHeaderAndMatchingErrorTraceId() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/money-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"))
                .andReturn();

        String requestId = result.getResponse().getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        assertNotNull(requestId);
        assertTrue(requestId.matches(REQUEST_ID_PATTERN));
        assertTrue(result.getResponse().getContentAsString().contains("\"traceId\":\"" + requestId + "\""));
        assertTrue(securityAppender.list.stream().anyMatch(event -> event.getLevel() == Level.WARN
                && event.getFormattedMessage().startsWith("security.access_denied")
                && requestId.equals(event.getMDCPropertyMap().get(RequestIds.MDC_KEY))));
        assertTrue(securityAppender.list.stream().noneMatch(event -> event.getLevel() == Level.ERROR));
        assertEquals(requestId, result.getResponse().getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER));
    }
}
