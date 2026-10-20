package com.budgetingapp.service;

import com.budgetingapp.config.SecurityConfig;
import com.budgetingapp.dto.request.LoginRequest;
import com.budgetingapp.dto.request.RegisterRequest;
import com.budgetingapp.dto.response.UserProfileResponse;
import com.budgetingapp.entity.User;
import com.budgetingapp.entity.enums.AccountStatus;
import com.budgetingapp.entity.enums.EmploymentType;
import com.budgetingapp.exception.BusinessException;
import com.budgetingapp.exception.LoginRateLimitedException;
import com.budgetingapp.repository.UserRepository;
import com.budgetingapp.security.ClientIpResolver;
import com.budgetingapp.security.LoginRateLimiter;
import com.budgetingapp.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String RAW_PASSWORD = "correct-horse-battery-42";
    private static final String GENERIC_FAILURE = "Email or password is incorrect.";

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private SecurityContextRepository securityContextRepository;

    private PasswordEncoder passwordEncoder;
    private LoginRateLimiter loginRateLimiter;
    private ClientIpResolver clientIpResolver;
    private AuthService authService;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        passwordEncoder = new SecurityConfig().passwordEncoder();
        loginRateLimiter = new LoginRateLimiter(10, Duration.ofMinutes(15), Clock.systemUTC());
        clientIpResolver = new ClientIpResolver(false);
        authService = new AuthService(userRepository, authenticationManager, passwordEncoder,
                securityContextRepository, loginRateLimiter, clientIpResolver);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registerStoresArgon2idPrefixedHashAndNormalizesEmail() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(7L);
            return user;
        });
        when(userRepository.findByUserId(7L)).thenAnswer(invocation -> {
            User user = savedUser(7L, "user@example.com");
            user.setCreatedAtUtc(Instant.parse("2026-10-18T00:00:00Z"));
            return Optional.of(user);
        });

        RegisterRequest registerRequest = registerRequest("  User Name ", "  USER@Example.COM ",
                "+919876543210", RAW_PASSWORD);
        UserProfileResponse profile = authService.register(registerRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User persisted = captor.getValue();
        assertEquals("user@example.com", persisted.getEmail());
        assertEquals("User Name", persisted.getFullName());
        assertEquals("+919876543210", persisted.getPhoneNumber());
        assertEquals(AccountStatus.ACTIVE, persisted.getAccountStatus());
        String storedHash = persisted.getPasswordHash();
        assertNotSameHash(RAW_PASSWORD, storedHash);
        assertTrue(storedHash.startsWith("{argon2}$argon2id$"));
        assertTrue(passwordEncoder.matches(RAW_PASSWORD, storedHash));
        assertFalse(passwordEncoder.matches("wrong-password-123", storedHash));
        assertFalse(storedHash.contains(RAW_PASSWORD));
        assertEquals(7L, profile.getUserId());
        assertEquals("user@example.com", profile.getEmail());
    }

    @Test
    void registerDuplicateEmailReturnsConflictWithoutSaving() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.register(registerRequest("User Name", " User@Example.COM ",
                        "+919876543210", RAW_PASSWORD)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("EMAIL_ALREADY_REGISTERED", exception.getCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void legacyBcryptPrefixedHashesStillAuthenticateAfterArgon2idSwitch() {
        String legacyHash = new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder()))
                .encode(RAW_PASSWORD);
        assertTrue(legacyHash.startsWith("{bcrypt}"));

        assertTrue(passwordEncoder.matches(RAW_PASSWORD, legacyHash));
        assertFalse(passwordEncoder.matches("wrong-password-123", legacyHash));
    }

    @Test
    void prefixLessLegacyHashesFallBackToBcryptVerification() {
        String legacyHash = new BCryptPasswordEncoder().encode(RAW_PASSWORD);
        assertTrue(legacyHash.startsWith("$2a$"));

        assertTrue(passwordEncoder.matches(RAW_PASSWORD, legacyHash));
        assertFalse(passwordEncoder.matches("wrong-password-123", legacyHash));
    }

    @Test
    void bcryptByteLimitDoesNotTruncateOrConfuseArgon2idHashes() {
        String sharedSeventyTwoBytePrefix = "A".repeat(72);
        String firstPassword = sharedSeventyTwoBytePrefix + "-first-tail-1";
        String secondPassword = sharedSeventyTwoBytePrefix + "-second-tail-2";

        String hash = passwordEncoder.encode(firstPassword);

        assertTrue(hash.startsWith("{argon2}$argon2id$"));
        assertTrue(passwordEncoder.matches(firstPassword, hash));
        assertFalse(passwordEncoder.matches(secondPassword, hash));
    }

    @Test
    void registerRoundTripsLongMultibyteUnicodePasswordWithoutTruncation() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(7L);
            return user;
        });
        when(userRepository.findByUserId(7L)).thenAnswer(invocation -> Optional.of(savedUser(7L, "user@example.com")));
        String longUnicodePassword = "пароль-密码-🔐-repeat".repeat(4);
        assertEquals(76, longUnicodePassword.length());

        authService.register(registerRequest("User Name", "user@example.com", "+919876543210", longUnicodePassword));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        String storedHash = captor.getValue().getPasswordHash();
        assertTrue(storedHash.startsWith("{argon2}$argon2id$"));
        assertTrue(passwordEncoder.matches(longUnicodePassword, storedHash));
        assertFalse(passwordEncoder.matches(longUnicodePassword + "x", storedHash));
        assertFalse(storedHash.contains(longUnicodePassword));
    }

    @Test
    void registerUniqueConstraintRaceReturnsConflictWithoutDatabaseDetails() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uq_users_email"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.register(registerRequest("User Name", "user@example.com",
                        "+919876543210", RAW_PASSWORD)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("EMAIL_ALREADY_REGISTERED", exception.getCode());
        String message = exception.getMessage();
        assertFalse(message.toLowerCase().contains("sql"));
        assertFalse(message.contains("uq_users_email"));
        assertFalse(message.toLowerCase().contains("violation"));
        assertFalse(message.toLowerCase().contains("integrity"));
        verify(userRepository, never()).findByUserId(any());
    }

    @Test
    void loginSuccessPersistsContextSessionAndProfile() {
        UserPrincipal principal = new UserPrincipal(5L, "user@example.com", "hash", true);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, RAW_PASSWORD,
                principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findByUserId(5L)).thenReturn(Optional.of(savedUser(5L, "user@example.com")));

        UserProfileResponse profile = authService.login(loginRequest("USER@example.com", RAW_PASSWORD),
                request, response);

        ArgumentCaptor<Authentication> authCaptor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(authCaptor.capture());
        assertEquals("user@example.com", authCaptor.getValue().getPrincipal());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertSame(principal, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertNotNull(request.getSession(false));
        ArgumentCaptor<SecurityContext> contextCaptor = ArgumentCaptor.forClass(SecurityContext.class);
        verify(securityContextRepository).saveContext(contextCaptor.capture(), eq(request), eq(response));
        assertSame(authentication, contextCaptor.getValue().getAuthentication());
        assertEquals(5L, profile.getUserId());
        assertEquals("user@example.com", profile.getEmail());
        assertEquals(AccountStatus.ACTIVE, profile.getAccountStatus());
    }

    @Test
    void loginBadCredentialsReturnsGenericErrorWithoutSessionOrContext() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("internal detail"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest("user@example.com", "wrong-password-123"), request, response));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("INVALID_CREDENTIALS", exception.getCode());
        assertEquals(GENERIC_FAILURE, exception.getMessage());
        assertFalse(exception.getMessage().contains("internal detail"));
        assertNull(request.getSession(false));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(securityContextRepository, never()).saveContext(any(), any(), any());
    }

    @Test
    void loginDisabledAccountReturnsSameGenericErrorAsBadCredentials() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("no such user"))
                .thenThrow(new DisabledException("account deactivated"));

        BusinessException badCredentials = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest("user@example.com", RAW_PASSWORD), request, response));
        BusinessException disabled = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest("user@example.com", RAW_PASSWORD), request, response));

        assertEquals(badCredentials.getCode(), disabled.getCode());
        assertEquals(badCredentials.getMessage(), disabled.getMessage());
        assertEquals(GENERIC_FAILURE, disabled.getMessage());
        assertNull(request.getSession(false));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(securityContextRepository, never()).saveContext(any(), any(), any());
    }

    @Test
    void loginDiscardsPreLoginSessionForSessionFixationProtection() {
        MockHttpSession preLoginSession = (MockHttpSession) request.getSession(true);
        preLoginSession.setAttribute("attackerMarker", "fixed-value");
        UserPrincipal principal = new UserPrincipal(5L, "user@example.com", "hash", true);
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(principal, RAW_PASSWORD, principal.getAuthorities()));
        when(userRepository.findByUserId(5L)).thenReturn(Optional.of(savedUser(5L, "user@example.com")));

        authService.login(loginRequest("user@example.com", RAW_PASSWORD), request, response);

        MockHttpSession currentSession = (MockHttpSession) request.getSession(false);
        assertNotNull(currentSession);
        assertTrue(preLoginSession.isInvalid());
        assertFalse(currentSession.isInvalid());
        assertNotSameInstance(preLoginSession, currentSession);
        assertNull(currentSession.getAttribute("attackerMarker"));
    }

    @Test
    void currentUserIdReadsAuthenticatedPrincipal() {
        UserPrincipal principal = new UserPrincipal(42L, "user@example.com", "hash", true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, "n/a",
                principal.getAuthorities()));
        SecurityContextHolder.setContext(context);

        assertEquals(42L, authService.currentUserId());
    }

    @Test
    void currentUserIdRejectsAnonymous() {
        BusinessException emptyContext = assertThrows(BusinessException.class, () -> authService.currentUserId());
        assertEquals(HttpStatus.UNAUTHORIZED, emptyContext.getStatus());
        assertEquals("NOT_AUTHENTICATED", emptyContext.getCode());

        SecurityContext anonymous = SecurityContextHolder.createEmptyContext();
        anonymous.setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(() -> "ROLE_ANONYMOUS")));
        SecurityContextHolder.setContext(anonymous);
        BusinessException anonymousContext = assertThrows(BusinessException.class, () -> authService.currentUserId());
        assertEquals("NOT_AUTHENTICATED", anonymousContext.getCode());
    }

    @Test
    void currentUserProfileLoadsByPrincipalUserId() {
        UserPrincipal principal = new UserPrincipal(9L, "user@example.com", "hash", true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, "n/a",
                principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        User user = savedUser(9L, "user@example.com");
        when(userRepository.findByUserId(9L)).thenReturn(Optional.of(user));

        UserProfileResponse profile = authService.currentUserProfile();

        verify(userRepository).findByUserId(9L);
        assertEquals(9L, profile.getUserId());
        assertEquals("user@example.com", profile.getEmail());
        assertThrows(NoSuchMethodException.class, () -> UserProfileResponse.class.getMethod("getPasswordHash"));
    }

    @Test
    void loginFailuresTripRateLimitAtThresholdBeforeAuthenticationRuns() {
        loginRateLimiter = new LoginRateLimiter(2, Duration.ofMinutes(15), Clock.systemUTC());
        authService = new AuthService(userRepository, authenticationManager, passwordEncoder,
                securityContextRepository, loginRateLimiter, clientIpResolver);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("internal"));

        BusinessException first = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest("user@example.com", "wrong-password-123"), request, response));
        BusinessException second = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest("user@example.com", "wrong-password-123"), request, response));

        assertEquals("INVALID_CREDENTIALS", first.getCode());
        assertEquals("INVALID_CREDENTIALS", second.getCode());
        LoginRateLimitedException limited = assertThrows(LoginRateLimitedException.class,
                () -> authService.login(loginRequest("user@example.com", "wrong-password-123"), request, response));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, limited.getStatus());
        assertEquals("LOGIN_RATE_LIMITED", limited.getCode());
        assertTrue(limited.getRetryAfterSeconds() >= 898 && limited.getRetryAfterSeconds() <= 900,
                "retry-after should be ~15 minutes, got " + limited.getRetryAfterSeconds());
        verify(authenticationManager, times(2)).authenticate(any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void successfulLoginDoesNotConsumeTheRateLimitBudget() {
        loginRateLimiter = new LoginRateLimiter(1, Duration.ofMinutes(15), Clock.systemUTC());
        authService = new AuthService(userRepository, authenticationManager, passwordEncoder,
                securityContextRepository, loginRateLimiter, clientIpResolver);
        UserPrincipal principal = new UserPrincipal(5L, "user@example.com", "hash", true);
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(principal, RAW_PASSWORD, principal.getAuthorities()))
                .thenThrow(new BadCredentialsException("internal"));
        when(userRepository.findByUserId(5L)).thenReturn(Optional.of(savedUser(5L, "user@example.com")));

        authService.login(loginRequest("user@example.com", RAW_PASSWORD), request, response);

        assertDoesNotThrow(() -> loginRateLimiter.checkAllowed("127.0.0.1"));

        BusinessException failure = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest("user@example.com", "wrong-password-123"), request, response));
        assertEquals("INVALID_CREDENTIALS", failure.getCode());
        assertThrows(LoginRateLimitedException.class, () -> loginRateLimiter.checkAllowed("127.0.0.1"));
    }

    @Test
    void deactivatedAccountFailuresCountTowardRateLimit() {
        loginRateLimiter = new LoginRateLimiter(1, Duration.ofMinutes(15), Clock.systemUTC());
        authService = new AuthService(userRepository, authenticationManager, passwordEncoder,
                securityContextRepository, loginRateLimiter, clientIpResolver);
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("account deactivated"));

        BusinessException disabled = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest("user@example.com", RAW_PASSWORD), request, response));

        assertEquals(GENERIC_FAILURE, disabled.getMessage());
        assertThrows(LoginRateLimitedException.class, () -> loginRateLimiter.checkAllowed("127.0.0.1"));
    }

    private void assertNotSameHash(String rawPassword, String storedHash) {
        assertFalse(storedHash.equals(rawPassword));
    }

    private void assertNotSameInstance(Object expected, Object actual) {
        assertFalse(expected == actual, "expected a fresh session instance");
    }

    private User savedUser(Long userId, String email) {
        User user = new User();
        user.setUserId(userId);
        user.setFullName("Test User");
        user.setEmail(email);
        user.setPhoneNumber("+919876543210");
        user.setEmploymentType(EmploymentType.SALARIED);
        user.setPasswordHash("{bcrypt}$2a$10$abcdefghijklmnopqrstuv");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setCreatedAtUtc(Instant.parse("2026-10-01T00:00:00Z"));
        return user;
    }

    private RegisterRequest registerRequest(String fullName, String email, String phone, String password) {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setFullName(fullName);
        registerRequest.setEmail(email);
        registerRequest.setPhoneNumber(phone);
        registerRequest.setEmploymentType(EmploymentType.SALARIED);
        registerRequest.setPassword(password);
        return registerRequest;
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword(password);
        return loginRequest;
    }
}
