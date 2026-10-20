package com.budgetingapp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String CSRF_SCHEME = "X-XSRF-TOKEN";

    @Bean
    public OpenAPI budgetingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Budgeting App API")
                        .description("Monthly Budgeting Money Planner backend API. Business endpoints require "
                                + "an authenticated session. POST, PUT and DELETE requests additionally require "
                                + "the CSRF token from the non-HttpOnly XSRF-TOKEN cookie to be sent in the "
                                + "X-XSRF-TOKEN header.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(CSRF_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-XSRF-TOKEN")
                                .description("Copy the value of the XSRF-TOKEN cookie and paste it here. "
                                        + "Required for POST, PUT and DELETE requests.")));
    }
}
