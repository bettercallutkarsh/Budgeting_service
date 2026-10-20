package com.budgetingapp.controller;

import com.budgetingapp.config.SecurityConfig;
import com.budgetingapp.config.TimeConfig;
import com.budgetingapp.dto.response.MoneyEntryPageResponse;
import com.budgetingapp.dto.response.MoneyEntryResponse;
import com.budgetingapp.entity.User;
import com.budgetingapp.entity.enums.AccountStatus;
import com.budgetingapp.entity.enums.EntryStatus;
import com.budgetingapp.entity.enums.EntryType;
import com.budgetingapp.entity.enums.EmploymentType;
import com.budgetingapp.exception.BusinessException;
import com.budgetingapp.exception.GlobalExceptionHandler;
import com.budgetingapp.repository.UserRepository;
import com.budgetingapp.service.AuthService;
import com.budgetingapp.service.MoneyEntryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, MoneyEntryController.class})
@Import({SecurityConfig.class, AuthService.class, GlobalExceptionHandler.class, TimeConfig.class})
class MoneyEntryControllerTest {

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
    void createReturns201AndUsesPrincipalIdentityIgnoringClientUserId() throws Exception {
        Session csrf = login();
        MoneyEntryResponse created = new MoneyEntryResponse();
        created.setMoneyEntryId(9L);
        created.setEntryType(EntryType.EXPENSE);
        created.setAmount(new BigDecimal("250.50"));
        created.setStatus(EntryStatus.POSTED);
        when(moneyEntryService.createEntry(eq(5L), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/money-entries")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 9999,
                                  "entryDate": "2026-10-18",
                                  "entryTime": "12:00:00",
                                  "entryType": "EXPENSE",
                                  "bucket": "ESSENTIALS",
                                  "categoryCode": "GROCERIES",
                                  "amount": "250.50",
                                  "paymentMethod": "UPI"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.moneyEntryId").value(9))
                .andExpect(jsonPath("$.status").value("POSTED"));
        verify(moneyEntryService).createEntry(eq(5L), any());
    }

    @Test
    void createWithMissingRequiredFieldsReturns400WithFieldErrors() throws Exception {
        Session csrf = login();

        mockMvc.perform(post("/api/v1/money-entries")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryType\": \"EXPENSE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'amount')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'entryDate')]").exists())
                .andExpect(jsonPath("$.traceId").exists());
        verifyNoInteractions(moneyEntryService);
    }

    @Test
    void listWithNoParamsPassesDefaultPaginationToService() throws Exception {
        Session csrf = login();
        when(moneyEntryService.listEntries(eq(5L), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/v1/money-entries").session(csrf.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
        verify(moneyEntryService).listEntries(eq(5L), argThat(query ->
                query.getPage() == null
                        && query.getSize() == null
                        && query.getEntryType() == null
                        && query.getStatus() == null));
    }

    @Test
    void listWithInvalidEnumValueReturns400() throws Exception {
        Session csrf = login();

        mockMvc.perform(get("/api/v1/money-entries")
                        .session(csrf.session())
                        .param("entryType", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"))
                .andExpect(jsonPath("$.traceId").exists());
        verifyNoInteractions(moneyEntryService);
    }

    @Test
    void listWithFilterParamsPassesParsedValuesToService() throws Exception {
        Session csrf = login();
        when(moneyEntryService.listEntries(eq(5L), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/v1/money-entries")
                        .session(csrf.session())
                        .param("entryType", "EXPENSE")
                        .param("bucket", "ESSENTIALS")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk());
        verify(moneyEntryService).listEntries(eq(5L), argThat(query ->
                query.getEntryType() == EntryType.EXPENSE
                        && Integer.valueOf(1).equals(query.getPage())
                        && Integer.valueOf(5).equals(query.getSize())));
    }

    @Test
    void getEntryDelegatesWithPrincipalIdentity() throws Exception {
        Session csrf = login();
        MoneyEntryResponse response = new MoneyEntryResponse();
        response.setMoneyEntryId(7L);
        response.setStatus(EntryStatus.POSTED);
        when(moneyEntryService.getEntry(5L, 7L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/money-entries/7")
                        .session(csrf.session())
                        .param("userId", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moneyEntryId").value(7));
        verify(moneyEntryService).getEntry(5L, 7L);
    }

    @Test
    void getEntryMissingReturns404() throws Exception {
        Session csrf = login();
        when(moneyEntryService.getEntry(5L, 7L))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "ENTRY_NOT_FOUND",
                        "Money entry not found."));

        mockMvc.perform(get("/api/v1/money-entries/7").session(csrf.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENTRY_NOT_FOUND"));
    }

    @Test
    void getEntryWithNonNumericIdReturns400() throws Exception {
        Session csrf = login();

        mockMvc.perform(get("/api/v1/money-entries/abc").session(csrf.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
        verifyNoInteractions(moneyEntryService);
    }

    @Test
    void voidWithoutBodyDefaultsToEmptyRequest() throws Exception {
        Session csrf = login();
        MoneyEntryResponse response = new MoneyEntryResponse();
        response.setMoneyEntryId(7L);
        response.setStatus(EntryStatus.VOID);
        when(moneyEntryService.voidEntry(eq(5L), eq(7L), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/money-entries/7/void")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOID"));
        ArgumentCaptor<com.budgetingapp.dto.request.MoneyEntryVoidRequest> captor =
                ArgumentCaptor.forClass(com.budgetingapp.dto.request.MoneyEntryVoidRequest.class);
        verify(moneyEntryService).voidEntry(eq(5L), eq(7L), captor.capture());
        assertNull(captor.getValue().getVoidReason());
    }

    @Test
    void voidWithReasonPassesReasonToService() throws Exception {
        Session csrf = login();
        when(moneyEntryService.voidEntry(eq(5L), eq(7L), any()))
                .thenAnswer(invocation -> new MoneyEntryResponse());

        mockMvc.perform(post("/api/v1/money-entries/7/void")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voidReason\": \"duplicate entry\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<com.budgetingapp.dto.request.MoneyEntryVoidRequest> captor =
                ArgumentCaptor.forClass(com.budgetingapp.dto.request.MoneyEntryVoidRequest.class);
        verify(moneyEntryService).voidEntry(eq(5L), eq(7L), captor.capture());
        assertEquals("duplicate entry", captor.getValue().getVoidReason());
    }

    @Test
    void voidConflictReturns409() throws Exception {
        Session csrf = login();
        when(moneyEntryService.voidEntry(eq(5L), eq(7L), any()))
                .thenThrow(new BusinessException(HttpStatus.CONFLICT, "ALREADY_VOIDED",
                        "This entry is already voided."));

        mockMvc.perform(post("/api/v1/money-entries/7/void")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_VOIDED"));
    }

    private MoneyEntryPageResponse emptyPage() {
        MoneyEntryPageResponse response = new MoneyEntryPageResponse();
        response.setContent(List.of());
        response.setPage(0);
        response.setSize(20);
        response.setTotalElements(0);
        response.setTotalPages(0);
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
