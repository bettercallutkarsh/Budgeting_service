package com.budgetingapp.security;

import com.budgetingapp.config.SecurityConfig;
import com.budgetingapp.config.TimeConfig;
import com.budgetingapp.controller.AuthController;
import com.budgetingapp.entity.User;
import com.budgetingapp.entity.enums.AccountStatus;
import com.budgetingapp.entity.enums.EmploymentType;
import com.budgetingapp.exception.GlobalExceptionHandler;
import com.budgetingapp.repository.UserRepository;
import com.budgetingapp.service.AuthService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, AuthService.class, GlobalExceptionHandler.class, TimeConfig.class})
class AuthSecurityFlowTest {

    private static final PasswordEncoder ENCODER =
            new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder()));
    private static final String PASSWORD = "correct-horse-battery-42";
    private static final String PASSWORD_HASH = ENCODER.encode(PASSWORD);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void csrfEndpointIssuesTokenMatchingNonHttpOnlyCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"))
                .andReturn();

        var cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        assertFalse(cookie.isHttpOnly());
        String body = responseBody(result);
        assertEquals(cookie.getValue(), JsonPath.read(body, "$.token"));
        assertFalse(body.contains(PASSWORD_HASH));

        MvcResult second = mockMvc.perform(get("/api/v1/auth/csrf").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(cookie.getValue(), JsonPath.read(responseBody(second), "$.token"));
    }

    @Test
    void registerWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("User Name", "user@example.com", "+919876543210", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"))
                .andExpect(jsonPath("$.traceId").exists());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerWithCsrfCreatesAccountAndIgnoresClientSuppliedUserId() throws Exception {
        var csrf = fetchCsrf();
        AtomicReference<User> saved = new AtomicReference<>();
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(7L);
            saved.set(user);
            return user;
        });
        when(userRepository.findByUserId(7L)).thenAnswer(invocation -> {
            User user = saved.get();
            if (user != null) {
                user.setCreatedAtUtc(Instant.parse("2026-10-18T00:00:00Z"));
            }
            return Optional.ofNullable(user);
        });

        String payload = """
                {
                  "userId": 9999,
                  "fullName": "  User Name  ",
                  "email": "  USER@Example.COM ",
                  "phoneNumber": "+919876543210",
                  "employmentType": "SALARIED",
                  "password": "%s"
                }
                """.formatted(PASSWORD);

        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.fullName").value("User Name"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
        verify(userRepository).existsByEmail("user@example.com");
    }

    @Test
    void newRegistrationStoresArgon2idHashThatIsNeverEchoedToClient() throws Exception {
        var csrf = fetchCsrf();
        AtomicReference<User> saved = new AtomicReference<>();
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(7L);
            saved.set(user);
            return user;
        });
        when(userRepository.findByUserId(7L)).thenAnswer(invocation -> {
            User user = saved.get();
            if (user != null) {
                user.setCreatedAtUtc(Instant.parse("2026-10-18T00:00:00Z"));
            }
            return Optional.ofNullable(user);
        });

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("User Name", "user@example.com", "+919876543210", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        User persisted = saved.get();
        assertNotNull(persisted);
        String storedHash = persisted.getPasswordHash();
        assertTrue(storedHash.startsWith("{argon2}$argon2id$"));
        assertTrue(passwordEncoder.matches(PASSWORD, storedHash));
        assertFalse(passwordEncoder.matches("wrong-password-123", storedHash));
        assertFalse(storedHash.contains(PASSWORD));
        String body = responseBody(result);
        assertFalse(body.contains(storedHash));
        assertFalse(body.contains("$2a$"));
        assertFalse(body.contains("argon2"));
    }

    @Test
    void loginSucceedsAgainstLegacyBcryptPrefixedStoredHash() throws Exception {
        String legacyHash = ENCODER.encode(PASSWORD);
        assertTrue(legacyHash.startsWith("{bcrypt}"));
        User legacyUser = user(5L, "user@example.com", legacyHash, AccountStatus.ACTIVE);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(legacyUser));
        when(userRepository.findByUserId(5L)).thenReturn(Optional.of(legacyUser));
        var csrf = fetchCsrf();

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("user@example.com", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(5))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registerValidationFailureReturns400WithFieldErrors() throws Exception {
        var csrf = fetchCsrf();

        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("User Name", "user@example.com", "+919876543210", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.traceId").exists());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void duplicateRegisterReturns409WithoutDatabaseDetails() throws Exception {
        var csrf = fetchCsrf();
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("User Name", "user@example.com", "+919876543210", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andReturn();

        String body = responseBody(result);
        assertEquals("An account with this email already exists.", JsonPath.read(body, "$.message"));
        assertFalse(body.toLowerCase().contains("sql"));
        assertFalse(body.toLowerCase().contains("constraint"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginReturnsProfileCreatesSessionAndMeResolvesIdentityServerSide() throws Exception {
        stubActiveUser();
        var csrf = fetchCsrf();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("  USER@Example.COM ", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(5))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);
        assertFalse(session.isInvalid());

        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(5))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void loginFailureResponsesAreIdenticalForUnknownEmailWrongPasswordAndDisabledAccount() throws Exception {
        var csrf = fetchCsrf();

        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(user(5L, "user@example.com", PASSWORD_HASH, AccountStatus.ACTIVE)));
        MvcResult wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("user@example.com", "totally-wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn();

        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        MvcResult unknownEmail = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("unknown@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn();

        when(userRepository.findByEmail("inactive@example.com"))
                .thenReturn(Optional.of(user(6L, "inactive@example.com", PASSWORD_HASH, AccountStatus.DEACTIVATED)));
        MvcResult disabledAccount = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("inactive@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn();

        String wrongPasswordBody = responseBody(wrongPassword);
        String unknownEmailBody = responseBody(unknownEmail);
        String disabledBody = responseBody(disabledAccount);

        String expectedMessage = "Email or password is incorrect.";
        assertEquals(expectedMessage, JsonPath.<String>read(wrongPasswordBody, "$.message"));
        assertEquals(JsonPath.<String>read(wrongPasswordBody, "$.code"), JsonPath.<String>read(unknownEmailBody, "$.code"));
        assertEquals(JsonPath.<String>read(wrongPasswordBody, "$.message"), JsonPath.<String>read(unknownEmailBody, "$.message"));
        assertEquals(JsonPath.<String>read(wrongPasswordBody, "$.code"), JsonPath.<String>read(disabledBody, "$.code"));
        assertEquals(JsonPath.<String>read(wrongPasswordBody, "$.message"), JsonPath.<String>read(disabledBody, "$.message"));
        for (String body : new String[]{wrongPasswordBody, unknownEmailBody, disabledBody}) {
            assertFalse(body.contains("$2a$"));
            assertFalse(body.contains("Exception"));
            assertFalse(body.contains("unknown@example.com"));
        }
        assertNull(wrongPassword.getRequest().getSession(false));
    }

    @Test
    void anonymousRequestsAreRejectedWithGeneric401() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource."));

        var csrf = fetchCsrf();
        mockMvc.perform(post("/api/v1/money-entries")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10.00}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
    }

    @Test
    void logoutInvalidatesSessionAndClearsAuthentication() throws Exception {
        stubActiveUser();
        var csrf = fetchCsrf();
        MockHttpSession session = performLogin(csrf, "user@example.com", PASSWORD);

        MvcResult logoutResult = mockMvc.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Signed out successfully."))
                .andReturn();

        assertTrue(session.isInvalid());
        assertNull(logoutResult.getRequest().getSession(false));
        assertNull(org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication());

        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutCsrfIsRejectedAndSessionSurvives() throws Exception {
        stubActiveUser();
        var csrf = fetchCsrf();
        MockHttpSession session = performLogin(csrf, "user@example.com", PASSWORD);

        mockMvc.perform(post("/api/v1/auth/logout").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        assertFalse(session.isInvalid());
    }

    @Test
    void loginIssuesFreshSessionDiscardingFixedPreLoginSession() throws Exception {
        stubActiveUser();
        var csrf = fetchCsrf();
        MockHttpSession fixedSession = new MockHttpSession();
        fixedSession.setAttribute("attackerMarker", "fixed-value");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .session(fixedSession)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("user@example.com", PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession freshSession = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(freshSession);
        assertTrue(fixedSession.isInvalid());
        assertNotSame(fixedSession, freshSession);
        assertNull(freshSession.getAttribute("attackerMarker"));
    }

    @Test
    void meIgnoresClientSuppliedUserIdParameters() throws Exception {
        stubActiveUser();
        var csrf = fetchCsrf();
        MockHttpSession session = performLogin(csrf, "user@example.com", PASSWORD);

        mockMvc.perform(get("/api/v1/me")
                        .session(session)
                        .param("userId", "9999")
                        .param("user_id", "9999")
                        .header("X-User-Id", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(5))
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void deactivatedAccountSessionIsRejectedMidSession() throws Exception {
        stubActiveUser();
        var csrf = fetchCsrf();
        MockHttpSession session = performLogin(csrf, "user@example.com", PASSWORD);

        User deactivated = user(5L, "user@example.com", PASSWORD_HASH, AccountStatus.DEACTIVATED);
        when(userRepository.findByUserId(5L)).thenReturn(Optional.of(deactivated));

        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DEACTIVATED"))
                .andExpect(jsonPath("$.traceId").exists());
        assertTrue(session.isInvalid());
        assertNull(org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication());
    }

    @Test
    void sessionCookieConfigurationIsHardenedByConfiguration() {
        assertEquals("true", environment.getProperty("server.servlet.session.cookie.http-only"));
        assertEquals("lax", environment.getProperty("server.servlet.session.cookie.same-site"));
        assertEquals("cookie", environment.getProperty("server.servlet.session.tracking-modes"));
        assertEquals("false", environment.getProperty("server.servlet.session.cookie.secure"));
        assertEquals("http://localhost:4200", environment.getProperty("app.cors.allowed-origins"));
        assertEquals("10", environment.getProperty("app.security.login-rate-limit.max-attempts"));
        assertEquals("15", environment.getProperty("app.security.login-rate-limit.window-minutes"));
        assertEquals("false", environment.getProperty("app.security.login-rate-limit.trust-proxy"));
    }

    private void stubActiveUser() {
        User user = user(5L, "user@example.com", PASSWORD_HASH, AccountStatus.ACTIVE);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByUserId(5L)).thenReturn(Optional.of(user));
    }

    private MockHttpSession performLogin(jakarta.servlet.http.Cookie csrf, String email,
                                         String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
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

    private String registerJson(String fullName, String email, String phone, String password) {
        return """
                {
                  "fullName": "%s",
                  "email": "%s",
                  "phoneNumber": "%s",
                  "employmentType": "SALARIED",
                  "password": "%s"
                }
                """.formatted(fullName, email, phone, password);
    }

    private String loginJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }
}
