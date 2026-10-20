package com.budgetingapp.controller;

import com.budgetingapp.dto.request.LoginRequest;
import com.budgetingapp.dto.request.RegisterRequest;
import com.budgetingapp.dto.response.CsrfTokenResponse;
import com.budgetingapp.dto.response.UserProfileResponse;
import com.budgetingapp.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;
    private final CookieCsrfTokenRepository csrfTokenRepository;

    public AuthController(AuthService authService, CookieCsrfTokenRepository csrfTokenRepository) {
        this.authService = authService;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/auth/login")
    public UserProfileResponse login(@Valid @RequestBody LoginRequest request,
                                     HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return authService.login(request, httpRequest, httpResponse);
    }

    @GetMapping("/auth/csrf")
    public CsrfTokenResponse csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokenRepository.loadToken(request);
        if (token == null) {
            token = csrfTokenRepository.generateToken(request);
            csrfTokenRepository.saveToken(token, request, response);
        }
        return new CsrfTokenResponse(token.getToken(), token.getHeaderName(), token.getParameterName());
    }

    @GetMapping("/me")
    public UserProfileResponse me() {
        return authService.currentUserProfile();
    }
}
