package com.budgetingapp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class OpenApiSecurityCustomizer implements OpenApiCustomizer {

    private static final List<PathItem.HttpMethod> MUTATING_METHODS = Arrays.asList(
            PathItem.HttpMethod.POST,
            PathItem.HttpMethod.PUT,
            PathItem.HttpMethod.PATCH,
            PathItem.HttpMethod.DELETE);

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi == null || openApi.getPaths() == null) {
            return;
        }

        SecurityRequirement csrfSecurityRequirement = new SecurityRequirement()
                .addList(OpenApiConfig.CSRF_SCHEME);

        for (Map.Entry<String, PathItem> entry : openApi.getPaths().entrySet()) {
            PathItem pathItem = entry.getValue();
            if (pathItem == null) {
                continue;
            }
            if (pathItem.getPost() != null) {
                addSecurityIfMissing(pathItem.getPost(), csrfSecurityRequirement);
            }
            if (pathItem.getPut() != null) {
                addSecurityIfMissing(pathItem.getPut(), csrfSecurityRequirement);
            }
            if (pathItem.getPatch() != null) {
                addSecurityIfMissing(pathItem.getPatch(), csrfSecurityRequirement);
            }
            if (pathItem.getDelete() != null) {
                addSecurityIfMissing(pathItem.getDelete(), csrfSecurityRequirement);
            }
        }
    }

    private void addSecurityIfMissing(Operation operation, SecurityRequirement csrfSecurityRequirement) {
        if (operation != null && (operation.getSecurity() == null || operation.getSecurity().isEmpty())) {
            operation.setSecurity(Collections.singletonList(csrfSecurityRequirement));
        }
    }
}
