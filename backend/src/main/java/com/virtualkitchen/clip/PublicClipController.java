package com.virtualkitchen.clip;

import com.virtualkitchen.clip.dto.ClipViewRequest;
import com.virtualkitchen.clip.dto.PublicClipResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Share links (/c/{token} in the web app). No login. */
@RestController
@RequestMapping("/api/public/clips/{token}")
public class PublicClipController {

    private final ClipPlaybackService service;

    public PublicClipController(ClipPlaybackService service) {
        this.service = service;
    }

    @GetMapping
    public PublicClipResponse get(@PathVariable String token) {
        return service.publicClip(token);
    }

    /** Called by the share page once it shows the clip. */
    @PostMapping("/views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void view(@PathVariable String token, @Valid @RequestBody ClipViewRequest request) {
        service.recordView(token, request.visitorId());
    }
}
