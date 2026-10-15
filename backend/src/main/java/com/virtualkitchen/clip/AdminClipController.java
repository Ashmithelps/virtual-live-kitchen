package com.virtualkitchen.clip;

import com.virtualkitchen.clip.dto.ClipPlaybackResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/clips")
@PreAuthorize("hasRole('ADMIN')")
public class AdminClipController {

    private final ClipPlaybackService service;

    private final com.virtualkitchen.admin.AdminAudit audit;

    public AdminClipController(ClipPlaybackService service, com.virtualkitchen.admin.AdminAudit audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/{orderId}/playback")
    public ClipPlaybackResponse playback(@org.springframework.security.core.annotation.AuthenticationPrincipal
                                         com.virtualkitchen.auth.security.AuthenticatedUser admin, @PathVariable UUID orderId) {
        return audit.audited(admin.id(), "CLIP_VIEWED", "order", orderId, java.util.Map.of(), () -> service.forAdmin(orderId));
    }
}
