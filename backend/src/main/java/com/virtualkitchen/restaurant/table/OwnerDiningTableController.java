package com.virtualkitchen.restaurant.table;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/owner/restaurants/{restaurantId}/tables")
@PreAuthorize("hasRole('RESTAURANT_OWNER')")
public class OwnerDiningTableController {

    private final DiningTableService service;

    public OwnerDiningTableController(DiningTableService service) {
        this.service = service;
    }

    @GetMapping
    public List<DiningTableResponse> list(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId) {
        return service.list(owner.id(), restaurantId);
    }

    /** Printable PDF, one QR code per page. Binary download, so no JSON DTO here. */
    @GetMapping(value = "/qr-codes.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> qrSheet(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId) {
        DiningTableService.QrSheet sheet = service.qrSheet(owner.id(), restaurantId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(sheet.filename()).build().toString())
                .body(sheet.pdf());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DiningTableResponse create(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                                      @Valid @RequestBody DiningTableRequest request) {
        return service.create(owner.id(), restaurantId, request);
    }

    @PutMapping("/{tableId}")
    public DiningTableResponse rename(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                                      @PathVariable UUID tableId, @Valid @RequestBody DiningTableRequest request) {
        return service.rename(owner.id(), restaurantId, tableId, request);
    }

    @PostMapping("/{tableId}/qr-token")
    public DiningTableResponse regenerateQrToken(@AuthenticationPrincipal AuthenticatedUser owner,
                                                 @PathVariable UUID restaurantId, @PathVariable UUID tableId) {
        return service.regenerateQrToken(owner.id(), restaurantId, tableId);
    }

    @DeleteMapping("/{tableId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                       @PathVariable UUID tableId) {
        service.delete(owner.id(), restaurantId, tableId);
    }
}
