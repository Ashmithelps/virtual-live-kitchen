package com.virtualkitchen.admin;

import com.virtualkitchen.admin.dto.AdminRestaurantDetailResponse;
import com.virtualkitchen.auth.dto.UserResponse;
import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.auth.user.UserAccountService;
import com.virtualkitchen.billing.AdminBillingService;
import com.virtualkitchen.billing.InvoiceService;
import com.virtualkitchen.restaurant.RestaurantService;
import com.virtualkitchen.restaurant.RestaurantStatus;
import com.virtualkitchen.restaurant.dto.ComplianceResponse;
import com.virtualkitchen.restaurant.dto.RestaurantResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/restaurants")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRestaurantController {

    private final RestaurantService restaurantService;
    private final UserAccountService users;
    private final AdminBillingService billing;
    private final InvoiceService invoices;
    private final AdminAudit audit;

    public AdminRestaurantController(RestaurantService restaurantService, UserAccountService users, AdminBillingService billing,
                                     InvoiceService invoices, AdminAudit audit) {
        this.restaurantService = restaurantService;
        this.users = users;
        this.billing = billing;
        this.invoices = invoices;
        this.audit = audit;
    }

    @GetMapping
    public List<RestaurantResponse> list(@RequestParam(defaultValue = "PENDING_APPROVAL") RestaurantStatus status) {
        return restaurantService.listByStatus(status);
    }

    /** Owner, FSSAI licence (with a short-lived link to the document) and billing. Opening it is audited. */
    @GetMapping("/{restaurantId}")
    public AdminRestaurantDetailResponse detail(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID restaurantId) {
        RestaurantResponse restaurant = restaurantService.getForAdmin(restaurantId);
        ComplianceResponse compliance = restaurantService.complianceForAdmin(restaurantId);
        audit.record(admin.id(), "RESTAURANT_VIEWED", "restaurant", restaurantId,
                Map.of("fssaiDocumentLinkIssued", compliance.fssaiDocUrl() != null));
        return new AdminRestaurantDetailResponse(restaurant, UserResponse.from(users.getById(restaurant.ownerId())), compliance,
                billing.subscription(restaurantId), invoices.list(restaurantId));
    }

    @PostMapping("/{restaurantId}/approve")
    public RestaurantResponse approve(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID restaurantId) {
        return audit.audited(admin.id(), "RESTAURANT_APPROVED", "restaurant", restaurantId, Map.of(),
                () -> restaurantService.changeStatusAsAdmin(restaurantId, RestaurantStatus.ACTIVE));
    }

    @PostMapping("/{restaurantId}/suspend")
    public RestaurantResponse suspend(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID restaurantId) {
        return audit.audited(admin.id(), "RESTAURANT_SUSPENDED", "restaurant", restaurantId, Map.of(),
                () -> restaurantService.changeStatusAsAdmin(restaurantId, RestaurantStatus.SUSPENDED));
    }
}
