package com.budgetingapp.controller;

import com.budgetingapp.config.SecurityConfig;
import com.budgetingapp.config.TimeConfig;
import com.budgetingapp.dto.response.MonthlySummaryResponse;
import com.budgetingapp.dto.response.ReportSummaryResponse;
import com.budgetingapp.entity.User;
import com.budgetingapp.entity.enums.AccountStatus;
import com.budgetingapp.entity.enums.EmploymentType;
import com.budgetingapp.exception.BusinessException;
import com.budgetingapp.exception.GlobalExceptionHandler;
import com.budgetingapp.repository.UserRepository;
import com.budgetingapp.service.AuthService;
import com.budgetingapp.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, ReportController.class})
@Import({SecurityConfig.class, AuthService.class, GlobalExceptionHandler.class, TimeConfig.class})
class ReportControllerTest {

    private static final PasswordEncoder ENCODER =
            new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder()));
    private static final String PASSWORD = "correct-horse-battery-42";
    private static final String PASSWORD_HASH = ENCODER.encode(PASSWORD);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private ReportService reportService;

    @Test
    void monthlySummaryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/reports/monthly-summary").param("month", "2026-10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
        verifyNoInteractions(reportService);
    }

    @Test
    void monthlySummaryWithoutMonthReturns400() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/v1/reports/monthly-summary").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MONTH_FORMAT_INVALID"))
                .andExpect(jsonPath("$.traceId").exists());
        verifyNoInteractions(reportService);
    }

    @Test
    void monthlySummaryWithInvalidMonthReturns400() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/v1/reports/monthly-summary")
                        .session(session)
                        .param("month", "2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MONTH_FORMAT_INVALID"));
        verifyNoInteractions(reportService);
    }

    @Test
    void monthlySummaryDelegatesWithPrincipalIdentityAndParsedMonth() throws Exception {
        MockHttpSession session = login();
        MonthlySummaryResponse response = new MonthlySummaryResponse();
        response.setMonth("2026-10");
        response.setStatus(MonthlySummaryResponse.MonthStatus.BUDGET_NOT_SET);
        when(reportService.monthlySummary(5L, YearMonth.of(2026, 10))).thenReturn(response);

        mockMvc.perform(get("/api/v1/reports/monthly-summary")
                        .session(session)
                        .param("month", "2026-10")
                        .param("userId", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.status").value("BUDGET_NOT_SET"))
                .andExpect(jsonPath("$.incomeAmount").doesNotExist());
        verify(reportService).monthlySummary(5L, YearMonth.of(2026, 10));
    }

    @Test
    void summaryWithMalformedDateReturns400() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/v1/reports/summary")
                        .session(session)
                        .param("fromDate", "not-a-date")
                        .param("toDate", "2026-10-31")
                        .param("groupBy", "MONTH"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"))
                .andExpect(jsonPath("$.traceId").exists());
        verifyNoInteractions(reportService);
    }

    @Test
    void summaryWithoutDatesReturns400FromServiceValidation() throws Exception {
        MockHttpSession session = login();
        when(reportService.summary(eq(5L), isNull(), isNull(), isNull()))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST, "REPORT_RANGE_INCOMPLETE",
                        "fromDate is required."));

        mockMvc.perform(get("/api/v1/reports/summary").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORT_RANGE_INCOMPLETE"));
    }

    @Test
    void summaryPassesRawGroupByForServiceNormalization() throws Exception {
        MockHttpSession session = login();
        ReportSummaryResponse response = new ReportSummaryResponse();
        response.setFromDate(LocalDate.of(2026, 10, 1));
        response.setToDate(LocalDate.of(2026, 10, 31));
        response.setGroupBy("MONTH");
        response.setPeriods(List.of());
        when(reportService.summary(5L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "month"))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/reports/summary")
                        .session(session)
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-31")
                        .param("groupBy", "month")
                        .param("userId", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("MONTH"))
                .andExpect(jsonPath("$.fromDate").value("2026-10-01"))
                .andExpect(jsonPath("$.toDate").value("2026-10-31"));
        verify(reportService).summary(5L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "month");
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
        return session;
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
