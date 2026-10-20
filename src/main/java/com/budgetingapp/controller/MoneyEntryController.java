package com.budgetingapp.controller;

import com.budgetingapp.dto.request.MoneyEntryCreateRequest;
import com.budgetingapp.dto.request.MoneyEntryListQuery;
import com.budgetingapp.dto.request.MoneyEntryVoidRequest;
import com.budgetingapp.dto.response.MoneyEntryPageResponse;
import com.budgetingapp.dto.response.MoneyEntryResponse;
import com.budgetingapp.service.AuthService;
import com.budgetingapp.service.MoneyEntryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/money-entries")
public class MoneyEntryController {

    private final MoneyEntryService moneyEntryService;
    private final AuthService authService;

    public MoneyEntryController(MoneyEntryService moneyEntryService, AuthService authService) {
        this.moneyEntryService = moneyEntryService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<MoneyEntryResponse> createEntry(@Valid @RequestBody MoneyEntryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(moneyEntryService.createEntry(authService.currentUserId(), request));
    }

    @GetMapping
    public MoneyEntryPageResponse listEntries(@Valid @ModelAttribute MoneyEntryListQuery query) {
        return moneyEntryService.listEntries(authService.currentUserId(), query);
    }

    @GetMapping("/{id}")
    public MoneyEntryResponse getEntry(@PathVariable("id") Long id) {
        return moneyEntryService.getEntry(authService.currentUserId(), id);
    }

    @PostMapping("/{id}/void")
    public MoneyEntryResponse voidEntry(@PathVariable("id") Long id,
                                        @RequestBody(required = false) MoneyEntryVoidRequest request) {
        return moneyEntryService.voidEntry(authService.currentUserId(), id,
                request != null ? request : new MoneyEntryVoidRequest());
    }
}
