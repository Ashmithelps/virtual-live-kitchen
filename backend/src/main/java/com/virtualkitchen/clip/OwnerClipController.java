package com.virtualkitchen.clip;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.clip.dto.ClipPlaybackResponse;
import com.virtualkitchen.clip.dto.OwnerClipResponse;
import com.virtualkitchen.common.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/owner/restaurants/{restaurantId}/clips")
@PreAuthorize("hasRole('RESTAURANT_OWNER')")
public class OwnerClipController {

    private final OwnerClipService service;
    private final ClipPlaybackService playback;

    public OwnerClipController(OwnerClipService service, ClipPlaybackService playback) {
        this.service = service;
        this.playback = playback;
    }

    /** Newest first; {@code status} filters (e.g. FAILED). */
    @GetMapping
    public PageResponse<OwnerClipResponse> list(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                                                @RequestParam(required = false) ClipState status,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(owner.id(), restaurantId, status, page, size);
    }

    /** 10-minute watch and download links for one READY clip. */
    @GetMapping("/{orderId}/playback")
    public ClipPlaybackResponse playback(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                                         @PathVariable UUID orderId) {
        return playback.forOwner(owner.id(), restaurantId, orderId);
    }

    @PostMapping("/{orderId}/retry")
    public OwnerClipResponse retry(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                                   @PathVariable UUID orderId) {
        return service.retry(owner.id(), restaurantId, orderId);
    }
}
