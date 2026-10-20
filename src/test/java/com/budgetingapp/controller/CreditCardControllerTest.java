package com.budgetingapp.controller;

import com.budgetingapp.config.SecurityConfig;
import com.budgetingapp.config.TimeConfig;
import com.budgetingapp.dto.response.CreditCardSummaryResponse;
import com.budgetingapp.entity.User;
import com.budgetingapp.entity.enums.AccountStatus;
import com.budgetingapp.entity.enums.EmploymentType;
import com.budgetingapp.exception.GlobalExceptionHandler;
import com.budgetingapp.repository.UserRepository;
import com.budgetingapp.service.AuthService;
import com.budgetingapp.service.MoneyEntryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, CreditCardController.class})
@Import({SecurityConfig.class, AuthService.class, GlobalExceptionHandler.class, TimeConfig.class})
class CreditCardControllerTest {

    private static final PasswordEncoder ENCODER =
            new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder()));
    private static final String PASSWORD = "correct-horse-battery-42";
    private static final String PASSWORD_HASH = ENCODER.encode(PASSWORD);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MoneyEntryService moneyEntryService;

    @Test
    void summaryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/credit-card/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
        verifyNoInteractions(moneyEntryService);
    }

    @Test
    void summaryDelegatesToServiceWithPrincipalIdentity() throws Exception {
        MockHttpSession session = login();
        CreditCardSummaryResponse response = new CreditCardSummaryResponse();
        response.setTrackingConfigured(Boolean.TRUE);
        response.setOpeningOutstandingAmount(new BigDecimal("5000.00"));
        response.setCurrentOutstandingAmount(new BigDecimal("4500.00"));
        when(moneyEntryService.getCreditCardSummary(5L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/credit-card/summary")
                        .session(session)
                        .param("userId", "9999")
                        .header("X-User-Id", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingConfigured").value(true))
                .andExpect(jsonPath("$.currentOutstandingAmount").value("4500.00"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(moneyEntryService).getCreditCardSummary(5L);
    }

    @Test
    void openingOutstandingUsesPrincipalIdentityAndIgnoresBodyUserId() throws Exception {
        MockHttpSession session = login();
        when(moneyEntryService.setCreditCardOpening(eq(5L), any()))
                .thenAnswer(invocation -> new CreditCardSummaryResponse());

        mockMvc.perform(put("/api/v1/credit-card/opening-outstanding")
                        .session(session)
                        .cookie(csrf(session))
                        .header("X-XSRF-TOKEN", csrfValue(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 9999,
                                  "openingOutstandingAmount": "1200.00",
                                  "trackingStartedAtUtc": "2026-10-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isOk());
        ArgumentCaptor<com.budgetingapp.dto.request.CreditCardOpeningRequest> captor =
                ArgumentCaptor.forClass(com.budgetingapp.dto.request.CreditCardOpeningRequest.class);
        verify(moneyEntryService).setCreditCardOpening(eq(5L), captor.capture());
        assertEquals(new BigDecimal("1200.00"), captor.getValue().getOpeningOutstandingAmount());
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), captor.getValue().getTrackingStartedAtUtc());
    }

    @Test
    void openingOutstandingWithNegativeAmountReturns400WithFieldErrors() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(put("/api/v1/credit-card/opening-outstanding")
                        .session(session)
                        .cookie(csrf(session))
                        .header("X-XSRF-TOKEN", csrfValue(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingOutstandingAmount\": \"-1.00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("openingOutstandingAmount"))
                .andExpect(jsonPath("$.traceId").exists());
        verifyNoInteractions(moneyEntryService);
    }

    @Test
    void openingOutstandingWithoutAmountReturns400() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(put("/api/v1/credit-card/opening-outstanding")
                        .session(session)
                        .cookie(csrf(session))
                        .header("X-XSRF-TOKEN", csrfValue(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("openingOutstandingAmount"));
    }

    private MockHttpSession login() throws Exception {
        User user = new User();
        user.setUserId(5L);
        user.setFullName("Test User");
        user.setEmail("user@example.com");
        user.setPhoneNumber("+919876543210");
        user.setEmploymentType(EmploymentType.SALARIED);
        user.setPasswordHash(PASSWORD_HASH);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setCreatedAtUtc(Instant.parse("2026-10-01T00:00:00Z"));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByUserId(5L)).thenReturn(Optional.of(user));

        jakarta.servlet.http.Cookie csrf = fetchCsrf();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "%s"
                                }
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        session.setAttribute("test.csrf", csrf);
        return session;
    }

    @SuppressWarnings("unchecked")
    private jakarta.servlet.http.Cookie csrf(MockHttpSession session) {
        return (jakarta.servlet.http.Cookie) session.getAttribute("test.csrf");
    }

    private String csrfValue(MockHttpSession session) {
        return csrf(session).getValue();
    }

    private jakarta.servlet.http.Cookie fetchCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        var cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        return cookie;
    }
}
