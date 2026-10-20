package com.budgetingapp.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerAccessFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void swaggerUiHtmlRedirectsAnonymouslyWithoutAuthenticationChallenge() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }

    @Test
    void swaggerUiIndexAndAssetsAreAnonymous() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("swagger-ui")));
        mockMvc.perform(get("/swagger-ui/swagger-ui.css"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/swagger-ui-bundle.js"))
                .andExpect(status().isOk());
    }

    @Test
    void openApiJsonDocumentIsGeneratedAnonymouslyWithAllBusinessPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").isNotEmpty())
                .andExpect(jsonPath("$.info.title").value("Budgeting App API"))
                .andExpect(jsonPath("$.paths['/api/v1/budgets/{month}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/money-entries']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/money-entries/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reference-data/categories']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reports/monthly-summary']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reports/summary']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/credit-card/summary']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/credit-card/opening-outstanding']").exists())
                .andExpect(jsonPath("$.components.securitySchemes['X-XSRF-TOKEN'].type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes['X-XSRF-TOKEN'].in").value("header"))
                .andExpect(jsonPath("$.components.securitySchemes['X-XSRF-TOKEN'].name").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void openApiYamlVariantAndSwaggerConfigAreAnonymous() throws Exception {
        mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("openapi:")));
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"))
                .andExpect(jsonPath("$.configUrl").value("/v3/api-docs/swagger-config"));
    }

    @Test
    void businessApisStillRejectAnonymousRequestsWith401() throws Exception {
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
                    .andExpect(jsonPath("$.traceId").exists());
        }
    }

    @Test
    void anonymousBusinessWritesStillRequireCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/money-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }
}
