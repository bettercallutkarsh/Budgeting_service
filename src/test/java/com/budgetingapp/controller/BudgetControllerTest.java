package com.budgetingapp.controller;

import com.budgetingapp.config.SecurityConfig;
import com.budgetingapp.config.TimeConfig;
import com.budgetingapp.dto.response.MonthlyBudgetResponse;
import com.budgetingapp.entity.User;
import com.budgetingapp.entity.enums.AccountStatus;
import com.budgetingapp.entity.enums.EmploymentType;
import com.budgetingapp.exception.GlobalExceptionHandler;
import com.budgetingapp.exception.PeriodReadOnlyException;
import com.budgetingapp.repository.UserRepository;
import com.budgetingapp.service.AuthService;
import com.budgetingapp.service.MonthlyBudgetService;
import org.junit.jupiter.api.Test;
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
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, BudgetController.class})
@Import({SecurityConfig.class, AuthService.class, GlobalExceptionHandler.class, TimeConfig.class})
class BudgetControllerTest {

    private static final PasswordEncoder ENCODER =
            new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder()));
    private static final String PASSWORD = "correct-horse-battery-42";
    private static final String PASSWORD_HASH = ENCODER.encode(PASSWORD);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MonthlyBudgetService monthlyBudgetService;

    @Test
    void getBudgetRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/budgets/2026-10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
        verifyNoInteractions(monthlyBudgetService);
    }

    @Test
    void getBudgetWithInvalidMonthReturns400() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/v1/budgets/2026-13").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MONTH_FORMAT_INVALID"))
                .andExpect(jsonPath("$.traceId").exists());
        verifyNoInteractions(monthlyBudgetService);
    }

    @Test
    void getBudgetWithMalformedMonthReturns400() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/v1/budgets/october").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MONTH_FORMAT_INVALID"));
    }

    @Test
    void getBudgetWithoutStoredBudgetReturns404BudgetNotSet() throws Exception {
        MockHttpSession session = login();
        when(monthlyBudgetService.getBudget(5L, YearMonth.of(2026, 10))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/budgets/2026-10").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUDGET_NOT_SET"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void getBudgetReturnsStoredBudgetForPrincipalOnly() throws Exception {
        MockHttpSession session = login();
        MonthlyBudgetResponse response = new MonthlyBudgetResponse();
        response.setMonth("2026-10");
        response.setIncomeAmount(new BigDecimal("30000.00"));
        when(monthlyBudgetService.getBudget(5L, YearMonth.of(2026, 10))).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/v1/budgets/2026-10")
                        .session(session)
                        .param("userId", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.incomeAmount").value("30000.00"));
        verify(monthlyBudgetService).getBudget(5L, YearMonth.of(2026, 10));
    }

    @Test
    void saveBudgetUsesPrincipalIdentityAndIgnoresClientSuppliedUserFields() throws Exception {
        MockHttpSession session = login();
        MonthlyBudgetResponse saved = new MonthlyBudgetResponse();
        saved.setMonth("2026-10");
        when(monthlyBudgetService.saveBudget(eq(5L), eq(YearMonth.of(2026, 10)), any()))
                .thenReturn(saved);

        mockMvc.perform(put("/api/v1/budgets/2026-10")
                        .session(session)
                        .cookie(csrf(session))
                        .header("X-XSRF-TOKEN", csrfValue(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetJson(9999L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"));
        verify(monthlyBudgetService).saveBudget(eq(5L), eq(YearMonth.of(2026, 10)), any());
    }

    @Test
    void saveBudgetWithMissingRequiredFieldsReturns400WithFieldErrors() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(put("/api/v1/budgets/2026-10")
                        .session(session)
                        .cookie(csrf(session))
                        .header("X-XSRF-TOKEN", csrfValue(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incomeAmount\": \"30000.00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'incomeType')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'allocationMode')]").exists())
                .andExpect(jsonPath("$.traceId").exists());
        verifyNoInteractions(monthlyBudgetService);
    }

    @Test
    void saveBudgetWithMalformedJsonReturns400RequestMalformed() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(put("/api/v1/budgets/2026-10")
                        .session(session)
                        .cookie(csrf(session))
                        .header("X-XSRF-TOKEN", csrfValue(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"));
    }

    @Test
    void saveBudgetPropagatesPeriodReadOnlyConflict() throws Exception {
        MockHttpSession session = login();
        when(monthlyBudgetService.saveBudget(eq(5L), eq(YearMonth.of(2026, 9)), any()))
                .thenThrow(new PeriodReadOnlyException("2026-09"));

        mockMvc.perform(put("/api/v1/budgets/2026-09")
                        .session(session)
                        .cookie(csrf(session))
                        .header("X-XSRF-TOKEN", csrfValue(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetJson(null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERIOD_READ_ONLY"));
    }

    @Test
    void saveBudgetWithInvalidMonthReturns400() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(put("/api/v1/budgets/not-a-month")
                        .session(session)
                        .cookie(csrf(session))
                        .header("X-XSRF-TOKEN", csrfValue(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetJson(null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MONTH_FORMAT_INVALID"));
        verify(monthlyBudgetService, never()).saveBudget(any(), any(), any());
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
        session.setAttribute("test.csrf.value", csrf.getValue());
        return session;
    }

    @SuppressWarnings("unchecked")
    private jakarta.servlet.http.Cookie csrf(MockHttpSession session) {
        return (jakarta.servlet.http.Cookie) session.getAttribute("test.csrf");
    }

    @SuppressWarnings("unchecked")
    private String csrfValue(MockHttpSession session) {
        return (String) session.getAttribute("test.csrf.value");
    }

    private jakarta.servlet.http.Cookie fetchCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        var cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        return cookie;
    }

    private String budgetJson(Long userId) {
        String userField = userId != null ? "\"userId\": %d,".formatted(userId) : "";
        return """
                {
                  %s
                  "incomeType": "SALARY",
                  "incomeSourceLabel": "Monthly salary",
                  "incomeAmount": "30000.00",
                  "allocationMode": "BALANCED",
                  "investmentsEnabled": true,
                  "essentialsPercentage": "55.00",
                  "safetyPercentage": "10.00",
                  "investmentsPercentage": "35.00"
                }
                """.formatted(userField);
    }
}
