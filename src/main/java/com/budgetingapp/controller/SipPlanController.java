package com.budgetingapp.controller;

import com.budgetingapp.dto.request.SipInstallmentPaymentRequest;
import com.budgetingapp.dto.request.SipPlanCreateRequest;
import com.budgetingapp.dto.request.SipPlanStopRequest;
import com.budgetingapp.dto.request.SipPlanUpdateRequest;
import com.budgetingapp.dto.response.SipCommitmentSummaryResponse;
import com.budgetingapp.dto.response.SipInstallmentListResponse;
import com.budgetingapp.dto.response.SipInstallmentPaymentResponse;
import com.budgetingapp.dto.response.SipPlanListResponse;
import com.budgetingapp.dto.response.SipPlanResponse;
import com.budgetingapp.service.AuthService;
import com.budgetingapp.service.SipPlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/sip-plans")
public class SipPlanController {

    private final SipPlanService sipPlanService;
    private final AuthService authService;

    public SipPlanController(SipPlanService sipPlanService, AuthService authService) {
        this.sipPlanService = sipPlanService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<SipPlanResponse> createPlan(@Valid @RequestBody SipPlanCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sipPlanService.createPlan(authService.currentUserId(), request));
    }

    @GetMapping
    public SipPlanListResponse listPlans() {
        return sipPlanService.listPlans(authService.currentUserId());
    }

    @GetMapping("/installments")
    public SipInstallmentListResponse listAllInstallments(
            @RequestParam(value = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) LocalDate toDate) {
        return sipPlanService.listInstallments(authService.currentUserId(), null, fromDate, toDate);
    }

    @GetMapping("/summary")
    public SipCommitmentSummaryResponse getCommitmentSummary(
            @RequestParam(value = "month", required = false) String month) {
        return sipPlanService.getCommitmentSummary(authService.currentUserId(), month);
    }

    @GetMapping("/{id}")
    public SipPlanResponse getPlan(@PathVariable("id") Long id) {
        return sipPlanService.getPlan(authService.currentUserId(), id);
    }

    @PutMapping("/{id}")
    public SipPlanResponse updatePlan(@PathVariable("id") Long id,
                                      @Valid @RequestBody SipPlanUpdateRequest request) {
        return sipPlanService.updatePlan(authService.currentUserId(), id, request);
    }

    @PostMapping("/{id}/stop")
    public SipPlanResponse stopPlan(@PathVariable("id") Long id,
                                    @RequestBody(required = false) SipPlanStopRequest request) {
        return sipPlanService.stopPlan(authService.currentUserId(), id,
                request != null ? request : new SipPlanStopRequest());
    }

    @GetMapping("/{id}/installments")
    public SipInstallmentListResponse listPlanInstallments(
            @PathVariable("id") Long id,
            @RequestParam(value = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) LocalDate toDate) {
        return sipPlanService.listInstallments(authService.currentUserId(), id, fromDate, toDate);
    }

    @PostMapping("/{id}/installments/{period}/pay")
    public ResponseEntity<SipInstallmentPaymentResponse> markInstallmentPaid(
            @PathVariable("id") Long id,
            @PathVariable("period") String period,
            @Valid @RequestBody(required = false) SipInstallmentPaymentRequest request) {
        SipInstallmentPaymentResponse response = sipPlanService.markPaid(
                authService.currentUserId(), id, period, request);
        HttpStatus status = Boolean.TRUE.equals(response.getIdempotentReplay())
                ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }
}
