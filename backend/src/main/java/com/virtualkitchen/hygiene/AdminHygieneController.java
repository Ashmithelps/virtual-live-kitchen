package com.virtualkitchen.hygiene;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.hygiene.dto.AdminDisputeResponse;
import com.virtualkitchen.hygiene.dto.HygieneSettingsRequest;
import com.virtualkitchen.hygiene.dto.HygieneSettingsResponse;
import com.virtualkitchen.hygiene.dto.ResolveDisputeRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Dispute review queue and the platform switch for public hygiene scores. */
@RestController
@RequestMapping("/api/admin/hygiene")
@PreAuthorize("hasRole('ADMIN')")
public class AdminHygieneController {

    private final HygieneService service;

    private final com.virtualkitchen.admin.AdminAudit audit;

    public AdminHygieneController(HygieneService service, com.virtualkitchen.admin.AdminAudit audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/disputes")
    public List<AdminDisputeResponse> disputes(@RequestParam(defaultValue = "OPEN") @Pattern(regexp = "OPEN|UPHELD|REJECTED") String status) {
        return service.disputes(status);
    }

    @PostMapping("/disputes/{disputeId}/resolve")
    public AdminDisputeResponse resolve(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID disputeId,
                                        @Valid @RequestBody ResolveDisputeRequest request) {
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("decision", request.decision().name());
        if (request.overrideScore() != null) details.put("overrideScore", request.overrideScore());
        if (Boolean.TRUE.equals(request.exclude())) details.put("exclude", true);
        if (request.note() != null) details.put("note", request.note());
        return audit.audited(admin.id(), "HYGIENE_DISPUTE_RESOLVED", "hygiene_dispute", disputeId, details,
                () -> service.resolve(admin.id(), disputeId, request));
    }

    @GetMapping("/settings")
    public HygieneSettingsResponse settings() {
        return service.settings();
    }

    /** Public scores stay off until the model's accuracy is validated. */
    @PutMapping("/settings")
    public HygieneSettingsResponse update(@AuthenticationPrincipal AuthenticatedUser admin, @Valid @RequestBody HygieneSettingsRequest request) {
        return audit.audited(admin.id(), "HYGIENE_PUBLIC_SCORES_SET", "platform", null,
                java.util.Map.of("publicScoresEnabled", request.publicScoresEnabled()),
                () -> service.updateSettings(admin.id(), request.publicScoresEnabled()));
    }
}
