package com.virtualkitchen.clip;

import com.virtualkitchen.clip.dto.ChunkUploadRequest;
import com.virtualkitchen.clip.dto.ChunkUploadUrl;
import com.virtualkitchen.clip.dto.ChunksUploadedRequest;
import com.virtualkitchen.clip.dto.ClipRequestResponse;
import com.virtualkitchen.kitchen.device.StationDevice;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Station phone uploads (X-Device-Token). */
@RestController
@RequestMapping("/api/clips/{orderId}")
@PreAuthorize("hasRole('STATION_DEVICE')")
public class ClipController {

    private final ClipService clips;

    public ClipController(ClipService clips) {
        this.clips = clips;
    }

    @PostMapping("/upload-urls")
    public List<ChunkUploadUrl> uploadUrls(@AuthenticationPrincipal StationDevice device, @PathVariable UUID orderId,
                                           @Valid @RequestBody ChunkUploadRequest request) {
        return clips.uploadUrls(device, orderId, request);
    }

    @PostMapping("/chunks-uploaded")
    public ClipRequestResponse chunksUploaded(@AuthenticationPrincipal StationDevice device, @PathVariable UUID orderId,
                                              @Valid @RequestBody ChunksUploadedRequest request) {
        return clips.chunksUploaded(device, orderId, request.objectKeys());
    }
}
