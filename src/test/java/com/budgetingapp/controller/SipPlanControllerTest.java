package com.budgetingapp.controller;

import com.budgetingapp.config.SecurityConfig;
import com.budgetingapp.config.TimeConfig;
import com.budgetingapp.dto.request.SipInstallmentPaymentRequest;
import com.budgetingapp.dto.request.SipPlanCreateRequest;
import com.budgetingapp.dto.response.MoneyEntryResponse;
import com.budgetingapp.dto.response.SipInstallmentListResponse;
import com.budgetingapp.dto.response.SipInstallmentPaymentResponse;
import com.budgetingapp.dto.response.SipInstallmentResponse;
import com.budgetingapp.dto.response.SipPlanListResponse;
import com.budgetingapp.dto.response.SipPlanResponse;
import com.budgetingapp.entity.User;
import com.budgetingapp.entity.enums.AccountStatus;
import com.budgetingapp.entity.enums.EmploymentType;
import com.budgetingapp.entity.enums.SipInstallmentStatus;
import com.budgetingapp.entity.enums.SipPlanStatus;
import com.budgetingapp.exception.GlobalExceptionHandler;
import com.budgetingapp.repository.UserRepository;
import com.budgetingapp.service.AuthService;
import com.budgetingapp.service.SipPlanService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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

@WebMvcTest(controllers = {AuthController.class, SipPlanController.class})
@Import({SecurityConfig.class, AuthService.class, GlobalExceptionHandler.class, TimeConfig.class})
class SipPlanControllerTest {

    private static final PasswordEncoder ENCODER =
            new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder()));
    private static final String PASSWORD = "correct-horse-battery-42";
    private static final String PASSWORD_HASH = ENCODER.encode(PASSWORD);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SipPlanService sipPlanService;

    @Test
    void createReturns201AndUsesAuthenticatedUser() throws Exception {
        Session csrf = login();
        when(sipPlanService.createPlan(eq(5L), any(SipPlanCreateRequest.class)))
                .thenReturn(planResponse());

        mockMvc.perform(post("/api/v1/sip-plans")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planName": "Retirement SIP",
                                  "monthlyAmount": "5000.00",
                                  "installmentDay": 5,
                                  "originalStartDate": "2026-09-01",
                                  "trackingStartDate": "2026-10-05",
                                  "plannedEndDate": "2030-12-31"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sipPlanId").value(11))
                .andExpect(jsonPath("$.investmentType").value("SIP"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        verify(sipPlanService).createPlan(eq(5L), any(SipPlanCreateRequest.class));
    }

    @Test
    void createWithMissingFieldsReturns400AndDoesNotCallService() throws Exception {
        Session csrf = login();

        mockMvc.perform(post("/api/v1/sip-plans")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planName\": \"Only name\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'monthlyAmount')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'installmentDay')]").exists());
        verifyNoInteractions(sipPlanService);
    }

    @Test
    void listReturnsPlansAndCommitment() throws Exception {
        Session csrf = login();
        SipPlanListResponse response = new SipPlanListResponse();
        response.setContent(List.of(planResponse()));
        response.setActiveMonthlyCommitment(new BigDecimal("5000.00"));
        when(sipPlanService.listPlans(5L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/sip-plans").session(csrf.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sipPlanId").value(11))
                .andExpect(jsonPath("$.activeMonthlyCommitment").value("5000.00"));
    }

    @Test
    void getPlanReturns404CodeFromService() throws Exception {
        Session csrf = login();
        when(sipPlanService.getPlan(5L, 11L)).thenThrow(
                new com.budgetingapp.exception.BusinessException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "SIP_PLAN_NOT_FOUND", "SIP plan not found."));

        mockMvc.perform(get("/api/v1/sip-plans/11").session(csrf.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SIP_PLAN_NOT_FOUND"));
    }

    @Test
    void updateReturnsUpdatedPlan() throws Exception {
        Session csrf = login();
        when(sipPlanService.updatePlan(eq(5L), eq(11L), any())).thenReturn(planResponse());

        mockMvc.perform(put("/api/v1/sip-plans/11")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planName": "Retirement SIP",
                                  "monthlyAmount": "5000.00",
                                  "installmentDay": 5,
                                  "originalStartDate": "2026-09-01",
                                  "trackingStartDate": "2026-10-05"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sipPlanId").value(11));
    }

    @Test
    void stopReturnsStoppedPlan() throws Exception {
        Session csrf = login();
        SipPlanResponse stopped = planResponse();
        stopped.setStatus(SipPlanStatus.STOPPED);
        stopped.setActualEndedDate(LocalDate.of(2026, 10, 20));
        when(sipPlanService.stopPlan(eq(5L), eq(11L), any())).thenReturn(stopped);

        mockMvc.perform(post("/api/v1/sip-plans/11/stop")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stopDate\":\"2026-10-20\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"));
    }

    @Test
    void installmentsPassDateRangeToService() throws Exception {
        Session csrf = login();
        SipInstallmentListResponse response = new SipInstallmentListResponse();
        response.setFromDate(LocalDate.of(2026, 10, 1));
        response.setToDate(LocalDate.of(2026, 10, 31));
        response.setContent(List.of(installment()));
        when(sipPlanService.listInstallments(5L, null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))).thenReturn(response);

        mockMvc.perform(get("/api/v1/sip-plans/installments")
                        .session(csrf.session())
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].period").value("2026-10"));
    }

    @Test
    void planInstallmentsPassPlanIdToService() throws Exception {
        Session csrf = login();
        SipInstallmentListResponse response = new SipInstallmentListResponse();
        response.setContent(List.of(installment()));
        when(sipPlanService.listInstallments(5L, 11L, null, null)).thenReturn(response);

        mockMvc.perform(get("/api/v1/sip-plans/11/installments").session(csrf.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sipPlanId").value(11));
    }

    @Test
    void markPaidReturns201ForNewPayment() throws Exception {
        Session csrf = login();
        when(sipPlanService.markPaid(eq(5L), eq(11L), eq("2026-10"),
                any(SipInstallmentPaymentRequest.class))).thenReturn(paymentResponse(false));

        mockMvc.perform(post("/api/v1/sip-plans/11/installments/2026-10/pay")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentDate\":\"2026-10-05\",\"paymentTime\":\"10:00:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idempotentReplay").value(false))
                .andExpect(jsonPath("$.moneyEntry.sipPlanId").value(11));
    }

    @Test
    void markPaidReturns200ForIdempotentReplay() throws Exception {
        Session csrf = login();
        when(sipPlanService.markPaid(eq(5L), eq(11L), eq("2026-10"),
                any(SipInstallmentPaymentRequest.class))).thenReturn(paymentResponse(true));

        mockMvc.perform(post("/api/v1/sip-plans/11/installments/2026-10/pay")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));
    }

    private SipPlanResponse planResponse() {
        SipPlanResponse response = new SipPlanResponse();
        response.setSipPlanId(11L);
        response.setInvestmentType("SIP");
        response.setPlanName("Retirement SIP");
        response.setMonthlyAmount(new BigDecimal("5000.00"));
        response.setInstallmentDay(5);
        response.setOriginalStartDate(LocalDate.of(2026, 9, 1));
        response.setTrackingStartDate(LocalDate.of(2026, 10, 5));
        response.setPlannedEndDate(LocalDate.of(2030, 12, 31));
        response.setStatus(SipPlanStatus.ACTIVE);
        return response;
    }

    private SipInstallmentResponse installment() {
        SipInstallmentResponse response = new SipInstallmentResponse();
        response.setSipPlanId(11L);
        response.setPlanName("Retirement SIP");
        response.setPeriod("2026-10");
        response.setDueDate(LocalDate.of(2026, 10, 5));
        response.setAmount(new BigDecimal("5000.00"));
        response.setStatus(SipInstallmentStatus.PAID);
        return response;
    }

    private SipInstallmentPaymentResponse paymentResponse(boolean replay) {
        MoneyEntryResponse entry = new MoneyEntryResponse();
        entry.setMoneyEntryId(77L);
        entry.setSipPlanId(11L);
        entry.setSipInstallmentPeriod(LocalDate.of(2026, 10, 1));
        SipInstallmentPaymentResponse response = new SipInstallmentPaymentResponse();
        response.setInstallment(installment());
        response.setMoneyEntry(entry);
        response.setIdempotentReplay(replay);
        return response;
    }

    private Session login() throws Exception {
        User user = new User();
        user.setUserId(5L);
        user.setFullName("Test User");
        user.setEmail("user@example.com");
        user.setPhoneNumber("+919876543210");
        user.setEmploymentType(EmploymentType.SALARIED);
        user.setPasswordHash(PASSWORD_HASH);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setCreatedAtUtc(Instant.parse("2026-10-01T00:00:00Z"));
        when(userRepository.findByEmail("user@example.com")).thenReturn(java.util.Optional.of(user));
        when(userRepository.findByUserId(5L)).thenReturn(java.util.Optional.of(user));

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
        return new Session(session, csrf);
    }

    private jakarta.servlet.http.Cookie fetchCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        var cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        return cookie;
    }

    private record Session(MockHttpSession session, jakarta.servlet.http.Cookie cookie) {
        String token() {
            return cookie.getValue();
        }
    }
}
