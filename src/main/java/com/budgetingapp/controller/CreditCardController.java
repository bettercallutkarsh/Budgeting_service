package com.budgetingapp.controller;

import com.budgetingapp.dto.request.CreditCardOpeningRequest;
import com.budgetingapp.dto.response.CreditCardSummaryResponse;
import com.budgetingapp.service.AuthService;
import com.budgetingapp.service.MoneyEntryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/credit-card")
public class CreditCardController {

    private final MoneyEntryService moneyEntryService;
    private final AuthService authService;

    public CreditCardController(MoneyEntryService moneyEntryService, AuthService authService) {
        this.moneyEntryService = moneyEntryService;
        this.authService = authService;
    }

    @GetMapping("/summary")
    public CreditCardSummaryResponse summary() {
        return moneyEntryService.getCreditCardSummary(authService.currentUserId());
    }

    @PutMapping("/opening-outstanding")
    public CreditCardSummaryResponse setOpeningOutstanding(
            @Valid @RequestBody CreditCardOpeningRequest request) {
        return moneyEntryService.setCreditCardOpening(authService.currentUserId(), request);
    }
}
