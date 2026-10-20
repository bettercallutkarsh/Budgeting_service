package com.budgetingapp.security;

import com.budgetingapp.config.SecurityConfig;
import com.budgetingapp.config.TimeConfig;
import com.budgetingapp.controller.AuthController;
import com.budgetingapp.controller.BudgetController;
import com.budgetingapp.controller.CategoryController;
import com.budgetingapp.controller.CreditCardController;
import com.budgetingapp.controller.MoneyEntryController;
import com.budgetingapp.controller.ReportController;
import com.budgetingapp.entity.User;
import com.budgetingapp.entity.enums.AccountStatus;
import com.budgetingapp.entity.enums.EmploymentType;
import com.budgetingapp.exception.GlobalExceptionHandler;
import com.budgetingapp.repository.UserRepository;
import com.budgetingapp.service.AuthService;
import com.budgetingapp.service.MonthlyBudgetService;
import com.budgetingapp.service.MoneyEntryService;
import com.budgetingapp.service.ReportService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, BudgetController.class, MoneyEntryController.class,
        CategoryController.class, ReportController.class, CreditCardController.class})
@Import({SecurityConfig.class, AuthService.class, GlobalExceptionHandler.class, TimeConfig.class})
class BusinessApiSecurityFlowTest {

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

    @MockitoBean
    private MoneyEntryService moneyEntryService;

    @MockitoBean
    private ReportService reportService;

    @Test
    void everyAuthenticatedBusinessGetRejectsAnonymousRequestsWith401() throws Exception {
        for (String path : List.of(
                "/api/v1/budgets/2026-10",
                "/api/v1/money-entries",
                "/api/v1/money-entries/7",
                "/api/v1/reference-data/categories",
                "/api/v1/reports/monthly-summary?month=2026-10",
                "/api/v1/reports/summary?fromDate=2026-10-01&toDate=2026-10-31&groupBy=MONTH",
                "/api/v1/credit-card/summary")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"))
                    .andExpect(jsonPath("$.traceId").exists())
                    .andExpect(jsonPath("$.message").value("Authentication is required to access this resource."));
        }
    }

    @Test
    void everyStateChangingBusinessEndpointRejectsAnonymousRequestsWith401() throws Exception {
        var csrf = fetchCsrf();
        for (MockHttpServletRequestBuilder request : List.of(
                budgetPut("2026-10"),
                post("/api/v1/money-entries").contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/api/v1/money-entries/7/void").contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/v1/credit-card/opening-outstanding")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))) {
            mockMvc.perform(request.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
        }
        verifyNoInteractions(monthlyBudgetService, moneyEntryService, reportService);
    }

    @Test
    void stateChangingEndpointsRejectAuthenticatedRequestsWithoutCsrfToken() throws Exception {
        stubActiveUser();
        var csrf = fetchCsrf();
        MockHttpSession session = performLogin(csrf);

        for (MockHttpServletRequestBuilder request : List.of(
                budgetPut("2026-10").session(session),
                post("/api/v1/money-entries").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/api/v1/money-entries/7/void").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/v1/credit-card/opening-outstanding").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))) {
            mockMvc.perform(request)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        }
        verifyNoInteractions(monthlyBudgetService, moneyEntryService, reportService);
    }

    @Test
    void authenticatedRequestsCarryServerSideIdentityOnly() throws Exception {
        stubActiveUser();
        var csrf = fetchCsrf();
        MockHttpSession session = performLogin(csrf);
        com.budgetingapp.dto.response.MonthlyBudgetResponse response =
                new com.budgetingapp.dto.response.MonthlyBudgetResponse();
        response.setMonth("2026-10");
        when(monthlyBudgetService.getBudget(5L, java.time.YearMonth.of(2026, 10)))
                .thenReturn(java.util.Optional.of(response));

        mockMvc.perform(get("/api/v1/budgets/2026-10")
                        .session(session)
                        .param("userId", "9999")
                        .header("X-User-Id", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"));

        verify(monthlyBudgetService).getBudget(5L, java.time.YearMonth.of(2026, 10));
    }

    private MockHttpServletRequestBuilder budgetPut(String month) {
        return put("/api/v1/budgets/" + month)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "incomeType": "SALARY",
                          "incomeAmount": "30000.00",
                          "allocationMode": "BALANCED",
                          "investmentsEnabled": true
                        }
                        """);
    }

    private void stubActiveUser() {
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
    }

    private MockHttpSession performLogin(jakarta.servlet.http.Cookie csrf) throws Exception {
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
}
