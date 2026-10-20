package com.budgetingapp.security;

import com.budgetingapp.config.SecurityConfig;
import com.budgetingapp.controller.AuthController;
import com.budgetingapp.entity.User;
import com.budgetingapp.entity.enums.AccountStatus;
import com.budgetingapp.entity.enums.EmploymentType;
import com.budgetingapp.exception.GlobalExceptionHandler;
import com.budgetingapp.repository.UserRepository;
import com.budgetingapp.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, AuthService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.security.login-rate-limit.max-attempts=3",
        "app.security.login-rate-limit.window-minutes=15",
        "app.security.login-rate-limit.trust-proxy=false"
})
class LoginRateLimitFlowTest {

    private static final Instant BASE = Instant.parse("2026-10-18T12:00:00Z");
    private static final PasswordEncoder HASH_FIXTURE =
            new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder()));
    private static final String PASSWORD = "correct-horse-battery-42";
    private static final String PASSWORD_HASH = HASH_FIXTURE.encode(PASSWORD);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @MockitoBean
    private UserRepository userRepository;

    @TestConfiguration
    static class MutableClockConfiguration {
        @Bean
        MutableClock mutableClock() {
            return new MutableClock(BASE, ZoneId.of("UTC"));
        }
    }

    @BeforeEach
    void setUp() {
        clock.set(BASE);
    }

    @Test
    void failedLoginsBelowThresholdStillReturnGeneric401() throws Exception {
        var csrf = fetchCsrf();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(loginPost(csrf, "10.2.0.1")
                            .content(loginJson("user@example.com", "wrong-password-123")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("Email or password is incorrect."))
                    .andExpect(jsonPath("$.traceId").exists());
        }
        assertEquals(2, loginRateLimiter.failureCount("10.2.0.1"));
    }

    @Test
    void thresholdFailureReturns429WithRetryAfterHeader() throws Exception {
        var csrf = fetchCsrf();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(loginPost(csrf, "10.2.1.1")
                        .content(loginJson("user@example.com", "wrong-password-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        assertEquals(1, loginRateLimiter.failureCount("10.2.1.1"));
        mockMvc.perform(loginPost(csrf, "10.2.1.1")
                        .content(loginJson("user@example.com", "wrong-password-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        assertEquals(2, loginRateLimiter.failureCount("10.2.1.1"));
        mockMvc.perform(loginPost(csrf, "10.2.1.1")
                        .content(loginJson("user@example.com", "wrong-password-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        assertEquals(3, loginRateLimiter.failureCount("10.2.1.1"));

        MvcResult limited = mockMvc.perform(loginPost(csrf, "10.2.1.1")
                        .content(loginJson("user@example.com", "wrong-password-123")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value("Too many failed login attempts. Please try again later."))
                .andExpect(jsonPath("$.traceId").exists())
                .andReturn();


        assertEquals("900", limited.getResponse().getHeader("Retry-After"));
        String body = responseBody(limited);
        assertFalse(body.contains("$2a$"));
        assertFalse(body.contains("argon2"));
        assertFalse(body.contains("Exception"));
        verify(userRepository, times(3)).findByEmail(any());
    }

    @Test
    void retryAfterShrinksOverTimeAndWindowExpiryRestoresLogin() throws Exception {
        var csrf = fetchCsrf();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        threeFailures(csrf, "10.2.2.1");
        assertRateLimited(csrf, "10.2.2.1", "900");

        clock.advance(Duration.ofMinutes(5));
        assertRateLimited(csrf, "10.2.2.1", "600");

        clock.advance(Duration.ofMinutes(9));
        assertRateLimited(csrf, "10.2.2.1", "60");

        clock.advance(Duration.ofMinutes(1));
        mockMvc.perform(loginPost(csrf, "10.2.2.1")
                        .content(loginJson("user@example.com", "wrong-password-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void rateLimitIsScopedPerClientIp() throws Exception {
        var csrf = fetchCsrf();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        threeFailures(csrf, "10.2.3.1");
        assertRateLimited(csrf, "10.2.3.1", "900");

        mockMvc.perform(loginPost(csrf, "10.2.3.2")
                        .content(loginJson("user@example.com", "wrong-password-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mockMvc.perform(loginPost(csrf, "10.2.3.3")
                        .content(loginJson("user@example.com", "wrong-password-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        assertEquals(1, loginRateLimiter.failureCount("10.2.3.2"));
        assertEquals(1, loginRateLimiter.failureCount("10.2.3.3"));
    }

    @Test
    void forwardedForHeaderIsIgnoredWhenTrustProxyIsDisabled() throws Exception {
        var csrf = fetchCsrf();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(loginPost(csrf, "10.2.4.1")
                            .header("X-Forwarded-For", "9.9.9.9")
                            .content(loginJson("user@example.com", "wrong-password-123")))
                    .andExpect(status().isUnauthorized());
        }
        MvcResult limited = mockMvc.perform(loginPost(csrf, "10.2.4.1")
                        .header("X-Forwarded-For", "9.9.9.9")
                        .content(loginJson("user@example.com", "wrong-password-123")))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        assertNotNull(limited.getResponse().getHeader("Retry-After"));
        assertEquals(3, loginRateLimiter.failureCount("10.2.4.1"));
        assertEquals(0, loginRateLimiter.failureCount("9.9.9.9"));
    }

    @Test
    void successfulLoginsDoNotCountAgainstTheRateLimit() throws Exception {
        var csrf = fetchCsrf();
        User activeUser = user(5L, "user@example.com", PASSWORD_HASH, AccountStatus.ACTIVE);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser));
        when(userRepository.findByUserId(5L)).thenReturn(Optional.of(activeUser));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(loginPost(csrf, "10.2.5.1")
                            .content(loginJson("user@example.com", PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(5));
        }
        assertEquals(0, loginRateLimiter.failureCount("10.2.5.1"));

        mockMvc.perform(loginPost(csrf, "10.2.5.1")
                        .content(loginJson("user@example.com", "wrong-password-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mockMvc.perform(loginPost(csrf, "10.2.5.1")
                        .content(loginJson("user@example.com", "wrong-password-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mockMvc.perform(loginPost(csrf, "10.2.5.1")
                        .content(loginJson("user@example.com", "wrong-password-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        assertEquals(3, loginRateLimiter.failureCount("10.2.5.1"));
        mockMvc.perform(loginPost(csrf, "10.2.5.1")
                        .content(loginJson("user@example.com", "wrong-password-123")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
    }

    private void threeFailures(jakarta.servlet.http.Cookie csrf, String ip) throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(loginPost(csrf, ip)
                            .content(loginJson("user@example.com", "wrong-password-123")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    private void assertRateLimited(jakarta.servlet.http.Cookie csrf, String ip, String expectedRetryAfter)
            throws Exception {
        MvcResult result = mockMvc.perform(loginPost(csrf, ip)
                        .content(loginJson("user@example.com", "wrong-password-123")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"))
                .andReturn();
        assertEquals(expectedRetryAfter, result.getResponse().getHeader("Retry-After"));
    }

    private MockHttpServletRequestBuilder loginPost(jakarta.servlet.http.Cookie csrf, String ip) {
        return post("/api/v1/auth/login")
                .remoteAddress(ip)
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON);
    }

    private jakarta.servlet.http.Cookie fetchCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        var cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        return cookie;
    }

    private String responseBody(MvcResult result) throws java.io.UnsupportedEncodingException {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String loginJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }

    private User user(Long userId, String email, String passwordHash, AccountStatus accountStatus) {
        User user = new User();
        user.setUserId(userId);
        user.setFullName("Test User");
        user.setEmail(email);
        user.setPhoneNumber("+919876543210");
        user.setEmploymentType(EmploymentType.SALARIED);
        user.setPasswordHash(passwordHash);
        user.setAccountStatus(accountStatus);
        user.setCreatedAtUtc(Instant.parse("2026-10-01T00:00:00Z"));
        return user;
    }
}
