package com.budgetingapp.service;

import com.budgetingapp.dto.request.LoginRequest;
import com.budgetingapp.dto.request.RegisterRequest;
import com.budgetingapp.dto.response.UserProfileResponse;
import com.budgetingapp.entity.User;
import com.budgetingapp.entity.enums.AccountStatus;
import com.budgetingapp.exception.BusinessException;
import com.budgetingapp.repository.UserRepository;
import com.budgetingapp.security.ClientIpResolver;
import com.budgetingapp.security.LoginRateLimiter;
import com.budgetingapp.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String GENERIC_LOGIN_FAILURE_MESSAGE = "Email or password is incorrect.";

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;
    private final LoginRateLimiter loginRateLimiter;
    private final ClientIpResolver clientIpResolver;

    public AuthService(UserRepository userRepository, AuthenticationManager authenticationManager,
                       PasswordEncoder passwordEncoder, SecurityContextRepository securityContextRepository,
                       LoginRateLimiter loginRateLimiter, ClientIpResolver clientIpResolver) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.securityContextRepository = securityContextRepository;
        this.loginRateLimiter = loginRateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    public UserProfileResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw duplicateEmailConflict();
        }
        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(email);
        user.setPhoneNumber(request.getPhoneNumber());
        user.setEmploymentType(request.getEmploymentType());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setAccountStatus(AccountStatus.ACTIVE);
        try {
            User saved = userRepository.save(user);
            User refreshed = userRepository.findByUserId(saved.getUserId()).orElseThrow(
                    () -> new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "USER_SAVE_FAILED",
                            "Unable to complete registration."));
            log.info("auth.registered");
            return toProfile(refreshed);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateEmailConflict();
        }
    }

    public UserProfileResponse login(LoginRequest request, HttpServletRequest httpRequest,
                                     HttpServletResponse httpResponse) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        loginRateLimiter.checkAllowed(clientIp);
        String email = normalizeEmail(request.getEmail());
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (BadCredentialsException | DisabledException | UsernameNotFoundException ex) {
            loginRateLimiter.recordFailure(clientIp);
            log.warn("auth.login_failed");
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    GENERIC_LOGIN_FAILURE_MESSAGE);
        }
        HttpSession preLoginSession = httpRequest.getSession(false);
        if (preLoginSession != null) {
            preLoginSession.invalidate();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        httpRequest.getSession(true);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        Long userId = ((UserPrincipal) authentication.getPrincipal()).getUserId();
        User user = userRepository.findByUserId(userId).orElseThrow(
                () -> new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        GENERIC_LOGIN_FAILURE_MESSAGE));
        log.info("auth.login_succeeded");
        return toProfile(user);
    }

    public UserProfileResponse currentUserProfile() {
        User user = userRepository.findByUserId(currentUserId()).orElseThrow(
                () -> new BusinessException(HttpStatus.UNAUTHORIZED, "NOT_AUTHENTICATED",
                        "Authentication is required to access this resource."));
        return toProfile(user);
    }

    public Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "NOT_AUTHENTICATED",
                    "Authentication is required to access this resource.");
        }
        return principal.getUserId();
    }

    private UserProfileResponse toProfile(User user) {
        UserProfileResponse profile = new UserProfileResponse();
        profile.setUserId(user.getUserId());
        profile.setFullName(user.getFullName());
        profile.setEmail(user.getEmail());
        profile.setPhoneNumber(user.getPhoneNumber());
        profile.setEmploymentType(user.getEmploymentType());
        profile.setAccountStatus(user.getAccountStatus());
        profile.setCreatedAtUtc(user.getCreatedAtUtc());
        return profile;
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private BusinessException duplicateEmailConflict() {
        return new BusinessException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                "An account with this email already exists.");
    }
}
