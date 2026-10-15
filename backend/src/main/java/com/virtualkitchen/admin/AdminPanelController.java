package com.virtualkitchen.admin;

import com.virtualkitchen.admin.dto.AuditEntryResponse;
import com.virtualkitchen.admin.dto.MetricsResponse;
import com.virtualkitchen.common.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPanelController {

    private final AdminMetricsService metrics;
    private final AdminAudit audit;

    public AdminPanelController(AdminMetricsService metrics, AdminAudit audit) {
        this.metrics = metrics;
        this.audit = audit;
    }

    @GetMapping("/metrics")
    public MetricsResponse metrics(@RequestParam(defaultValue = "30") @Min(1) @Max(180) int days) {
        return metrics.metrics(days);
    }

    /** Every admin action, newest first; {@code action} filters, e.g. RESTAURANT_SUSPENDED. */
    @GetMapping("/audit")
    public PageResponse<AuditEntryResponse> audit(@RequestParam(required = false) @Size(max = 60) String action,
                                                  @RequestParam(defaultValue = "0") @Min(0) int page,
                                                  @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return audit.list(action == null || action.isBlank() ? null : action, page, size);
    }
}
