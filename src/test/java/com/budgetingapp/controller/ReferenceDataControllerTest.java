package com.budgetingapp.controller;

import com.budgetingapp.config.SecurityConfig;
import com.budgetingapp.config.TimeConfig;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, CategoryController.class})
@Import({SecurityConfig.class, AuthService.class, GlobalExceptionHandler.class, TimeConfig.class})
class ReferenceDataControllerTest {

    private static final PasswordEncoder ENCODER =
            new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder()));
    private static final String PASSWORD = "correct-horse-battery-42";
    private static final String PASSWORD_HASH = ENCODER.encode(PASSWORD);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void categoriesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/reference-data/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
    }

    @Test
    void fullCatalogReturnsTwentyTwoCategoriesWithLabelsAndNoInternalFields() throws Exception {
        MockHttpSession session = login();

        MvcResult result = mockMvc.perform(get("/api/v1/reference-data/categories")
                        .session(session)
                        .param("userId", "9999"))
                .andExpect(status().isOk())
                .andReturn();

        String body = responseBody(result);
        List<String> codes = JsonPath.read(body, "$[*].code");
        assertEquals(22, codes.size());
        assertTrue(codes.contains("GROCERIES"));
        assertTrue(codes.contains("CREDIT_CARD_BILL_PAYMENT"));
        List<Object> groceryLabels = JsonPath.read(body, "$[?(@.code == 'GROCERIES')].label");
        assertEquals("Groceries", groceryLabels.get(0));
        List<Object> cardLabels = JsonPath.read(body, "$[?(@.code == 'CREDIT_CARD_BILL_PAYMENT')].label");
        assertEquals("Pay credit-card bill", cardLabels.get(0));
        List<Object> cardBuckets = JsonPath.read(body, "$[?(@.code == 'CREDIT_CARD_BILL_PAYMENT')].allowedBuckets");
        assertTrue(cardBuckets.toString().contains("[]"));
        assertFalse(body.contains("passwordHash"));
    }

    @Test
    void entryTypeFilterReturnsOnlyMatchingCategories() throws Exception {
        MockHttpSession session = login();

        MvcResult result = mockMvc.perform(get("/api/v1/reference-data/categories")
                        .session(session)
                        .param("entryType", "INVESTMENT")
                        .param("bucket", "INVESTMENTS"))
                .andExpect(status().isOk())
                .andReturn();

        List<String> codes = JsonPath.read(responseBody(result), "$[*].code");
        assertEquals(9, codes.size());
        assertTrue(codes.containsAll(List.of("SIP", "MUTUAL_FUND_LUMPSUM", "STOCKS", "FD", "RD", "PPF", "GOLD", "OTHER_INVESTMENT", "GOLD_SIP")));
        assertFalse(codes.contains("GROCERIES"));
        assertFalse(codes.contains("CREDIT_CARD_BILL_PAYMENT"));
    }

    @Test
    void bucketFilterReturnsDualBucketAndSafetySavingCodes() throws Exception {
        MockHttpSession session = login();

        MvcResult result = mockMvc.perform(get("/api/v1/reference-data/categories")
                        .session(session)
                        .param("bucket", "SAFETY"))
                .andExpect(status().isOk())
                .andReturn();

        List<String> codes = JsonPath.read(responseBody(result), "$[*].code");
        assertEquals(4, codes.size());
        assertTrue(codes.containsAll(List.of("MEDICAL", "OTHER_EXPENSE", "EMERGENCY_FUND", "SAFETY_SAVING")));
        assertFalse(codes.contains("GROCERIES"));
        assertFalse(codes.contains("SIP"));
    }

    @Test
    void invalidEnumFilterReturns400() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/v1/reference-data/categories")
                        .session(session)
                        .param("entryType", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"))
                .andExpect(jsonPath("$.traceId").exists());
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

    private String responseBody(MvcResult result) throws java.io.UnsupportedEncodingException {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
