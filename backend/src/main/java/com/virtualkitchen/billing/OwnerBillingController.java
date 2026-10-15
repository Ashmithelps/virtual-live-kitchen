package com.virtualkitchen.billing;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.billing.dto.BillingOverviewResponse;
import com.virtualkitchen.billing.dto.ChoosePlanRequest;
import com.virtualkitchen.billing.dto.ConfirmSubscriptionRequest;
import com.virtualkitchen.billing.dto.SubscriptionCheckoutResponse;
import com.virtualkitchen.restaurant.RestaurantAccess;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/owner/restaurants/{restaurantId}/billing")
@PreAuthorize("hasRole('RESTAURANT_OWNER')")
public class OwnerBillingController {

    private final BillingService billing;
    private final InvoiceService invoices;
    private final RestaurantAccess access;

    public OwnerBillingController(BillingService billing, InvoiceService invoices, RestaurantAccess access) {
        this.billing = billing;
        this.invoices = invoices;
        this.access = access;
    }

    @GetMapping
    public BillingOverviewResponse overview(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId) {
        return billing.overview(owner.id(), restaurantId);
    }

    /** Set up the recurring payment on a plan: returns what the gateway checkout needs. */
    @PostMapping("/checkout")
    public SubscriptionCheckoutResponse checkout(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                                                 @Valid @RequestBody ChoosePlanRequest request) {
        return billing.startCheckout(owner.id(), restaurantId, request.planId());
    }

    @PostMapping("/checkout/confirm")
    public BillingOverviewResponse confirm(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                                           @Valid @RequestBody ConfirmSubscriptionRequest request) {
        return billing.confirmCheckout(owner.id(), restaurantId, request);
    }

    /** Upgrade (now) or downgrade (at the end of the paid period). */
    @PutMapping("/plan")
    public BillingOverviewResponse changePlan(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                                              @Valid @RequestBody ChoosePlanRequest request) {
        return billing.changePlan(owner.id(), restaurantId, request.planId());
    }

    @PostMapping("/cancel")
    public BillingOverviewResponse cancel(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId) {
        return billing.cancel(owner.id(), restaurantId);
    }

    @GetMapping(value = "/invoices/{invoiceId}.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> invoice(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                                          @PathVariable UUID invoiceId) {
        access.requireOwned(restaurantId, owner.id());
        return pdf(invoices.pdf(restaurantId, invoiceId));
    }

    static ResponseEntity<byte[]> pdf(InvoiceService.Pdf pdf) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(pdf.filename()).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.bytes());
    }
}
