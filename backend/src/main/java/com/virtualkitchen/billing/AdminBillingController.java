package com.virtualkitchen.billing;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.billing.dto.AdminSubscriptionResponse;
import com.virtualkitchen.billing.dto.BillingSettingsRequest;
import com.virtualkitchen.billing.dto.BillingSettingsResponse;
import com.virtualkitchen.billing.dto.PlanRequest;
import com.virtualkitchen.billing.dto.PlanResponse;
import com.virtualkitchen.billing.dto.SimulateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/billing")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBillingController {

    private final AdminBillingService service;
    private final InvoiceService invoices;

    public AdminBillingController(AdminBillingService service, InvoiceService invoices) {
        this.service = service;
        this.invoices = invoices;
    }

    @GetMapping("/plans")
    public List<PlanResponse> plans() {
        return service.plans();
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse create(@AuthenticationPrincipal AuthenticatedUser admin, @Valid @RequestBody PlanRequest request) {
        return service.createPlan(admin.id(), request);
    }

    @PutMapping("/plans/{planId}")
    public PlanResponse update(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID planId, @Valid @RequestBody PlanRequest request) {
        return service.updatePlan(admin.id(), planId, request);
    }

    @GetMapping("/settings")
    public BillingSettingsResponse settings() {
        return service.settings();
    }

    @PutMapping("/settings")
    public BillingSettingsResponse updateSettings(@AuthenticationPrincipal AuthenticatedUser admin, @Valid @RequestBody BillingSettingsRequest request) {
        return service.updateSettings(admin.id(), request.trialDays(), request.graceDays());
    }

    @GetMapping("/subscriptions")
    public List<AdminSubscriptionResponse> subscriptions() {
        return service.subscriptions();
    }

    @PostMapping("/restaurants/{restaurantId}/simulate")
    public AdminSubscriptionResponse simulate(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID restaurantId,
                                              @Valid @RequestBody SimulateRequest request) {
        return service.simulate(admin.id(), restaurantId, request.event());
    }

    @GetMapping(value = "/restaurants/{restaurantId}/invoices/{invoiceId}.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> invoice(@PathVariable UUID restaurantId, @PathVariable UUID invoiceId) {
        return OwnerBillingController.pdf(invoices.pdf(restaurantId, invoiceId));
    }
}
