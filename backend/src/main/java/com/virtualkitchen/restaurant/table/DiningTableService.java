package com.virtualkitchen.restaurant.table;

import com.virtualkitchen.common.crypto.SecureTokens;
import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.restaurant.Restaurant;
import com.virtualkitchen.restaurant.RestaurantAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class DiningTableService {

    /** 32 random bytes → 43 url-safe chars; infeasible to guess another table's code. */
    private static final int QR_TOKEN_BYTES = 32;

    private final DiningTableRepository repository;
    private final RestaurantAccess restaurantAccess;
    private final TableLinks links;
    private final TableQrSheetRenderer sheetRenderer;
    private final Clock clock;

    public DiningTableService(DiningTableRepository repository, RestaurantAccess restaurantAccess, TableLinks links,
                              TableQrSheetRenderer sheetRenderer, Clock clock) {
        this.repository = repository;
        this.restaurantAccess = restaurantAccess;
        this.links = links;
        this.sheetRenderer = sheetRenderer;
        this.clock = clock;
    }

    /** One A4 page per table, in label order. */
    @Transactional(readOnly = true)
    public QrSheet qrSheet(UUID ownerId, UUID restaurantId) {
        Restaurant restaurant = restaurantAccess.requireOwned(restaurantId, ownerId);
        List<DiningTable> tables = repository.findByRestaurantIdOrderByLabelAsc(restaurantId);
        if (tables.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "Add at least one table before printing QR codes");
        }
        byte[] pdf = sheetRenderer.render(restaurant.getName(), tables.stream()
                .map(t -> new TableQrSheetRenderer.TableQr(t.getLabel(), links.qrUrl(t.getQrToken())))
                .toList());
        return new QrSheet(restaurant.getSlug() + "-table-qr-codes.pdf", pdf);
    }

    public record QrSheet(String filename, byte[] pdf) {
    }

    @Transactional(readOnly = true)
    public List<DiningTableResponse> list(UUID ownerId, UUID restaurantId) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        return repository.findByRestaurantIdOrderByLabelAsc(restaurantId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public DiningTableResponse create(UUID ownerId, UUID restaurantId, DiningTableRequest request) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        String label = request.label().strip();
        requireFreeLabel(restaurantId, label);
        return toResponse(repository.saveAndFlush(
                new DiningTable(restaurantId, label, SecureTokens.random(QR_TOKEN_BYTES), clock.instant())));
    }

    @Transactional
    public DiningTableResponse rename(UUID ownerId, UUID restaurantId, UUID tableId, DiningTableRequest request) {
        DiningTable table = requireTable(ownerId, restaurantId, tableId);
        String label = request.label().strip();
        if (!label.equalsIgnoreCase(table.getLabel())) {
            requireFreeLabel(restaurantId, label);
        }
        table.rename(label);
        return toResponse(table);
    }

    /**
     * Invalidates the old QR code and every table session started from it (e.g. after a printed
     * code was photographed and misused). Reprint the PDF afterwards.
     */
    @Transactional
    public DiningTableResponse regenerateQrToken(UUID ownerId, UUID restaurantId, UUID tableId) {
        DiningTable table = requireTable(ownerId, restaurantId, tableId);
        table.replaceQrToken(SecureTokens.random(QR_TOKEN_BYTES));
        return toResponse(table);
    }

    @Transactional
    public void delete(UUID ownerId, UUID restaurantId, UUID tableId) {
        repository.delete(requireTable(ownerId, restaurantId, tableId));
    }

    private DiningTable requireTable(UUID ownerId, UUID restaurantId, UUID tableId) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        return repository.findByIdAndRestaurantId(tableId, restaurantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Table not found"));
    }

    private DiningTableResponse toResponse(DiningTable table) {
        return new DiningTableResponse(table.getId(), table.getLabel(), table.getQrToken(),
                links.qrUrl(table.getQrToken()), table.getCreatedAt());
    }

    private void requireFreeLabel(UUID restaurantId, String label) {
        if (repository.existsByRestaurantIdAndLabelIgnoreCase(restaurantId, label)) {
            throw new ApiException(HttpStatus.CONFLICT, "A table labelled '" + label + "' already exists");
        }
    }
}
