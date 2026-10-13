package com.virtualkitchen.kitchen.device;

import com.virtualkitchen.clip.ClipService;
import com.virtualkitchen.clip.dto.ClipWindowResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Endpoints for the station phone (/station PWA). Authenticated with X-Device-Token. */
@RestController
@RequestMapping("/api/station-device")
public class StationDeviceController {

    private final StationDeviceService devices;
    private final ClipService clips;

    public StationDeviceController(StationDeviceService devices, ClipService clips) {
        this.devices = devices;
        this.clips = clips;
    }

    public record PairRequest(@NotBlank @Size(max = 100) String pairingCode) {
    }

    /** Public: the one-time code from the owner's pairing QR is the credential. */
    @PostMapping("/pair")
    public StationDeviceService.PairedDevice pair(@Valid @RequestBody PairRequest request) {
        return devices.pair(request.pairingCode());
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STATION_DEVICE')")
    public StationDeviceService.StationInfo me(@AuthenticationPrincipal StationDevice device) {
        return devices.describe(device);
    }

    /** Windows this station still has to upload (catch-up after being offline). */
    @GetMapping("/clip-requests")
    @PreAuthorize("hasRole('STATION_DEVICE')")
    public List<ClipWindowResponse> pending(@AuthenticationPrincipal StationDevice device) {
        return clips.pendingForStation(device);
    }
}
