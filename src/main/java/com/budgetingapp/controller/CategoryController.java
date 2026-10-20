package com.budgetingapp.controller;

import com.budgetingapp.dto.response.CategoryResponse;
import com.budgetingapp.entity.enums.BudgetBucket;
import com.budgetingapp.entity.enums.EntryType;
import com.budgetingapp.service.AuthService;
import com.budgetingapp.service.CategoryCatalog;
import com.budgetingapp.service.CategoryCatalog.CategoryDefinition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reference-data")
public class CategoryController {

    private final AuthService authService;

    public CategoryController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories(
            @RequestParam(name = "entryType", required = false) EntryType entryType,
            @RequestParam(name = "bucket", required = false) BudgetBucket bucket) {
        authService.currentUserId();
        return CategoryCatalog.catalog(entryType, bucket).stream()
                .map(CategoryController::toResponse)
                .toList();
    }

    private static CategoryResponse toResponse(CategoryDefinition definition) {
        CategoryResponse response = new CategoryResponse();
        response.setCode(definition.code());
        response.setLabel(definition.label());
        response.setAllowedEntryTypes(List.copyOf(definition.entryTypes()));
        response.setAllowedBuckets(List.copyOf(definition.buckets()));
        return response;
    }
}
