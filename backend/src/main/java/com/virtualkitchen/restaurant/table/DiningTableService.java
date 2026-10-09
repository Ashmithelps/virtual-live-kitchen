package com.virtualkitchen.restaurant.table;

import com.virtualkitchen.common.crypto.SecureTokens;
import com.virtualkitchen.common.error.ApiException;
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
    private final Clock clock;

    public DiningTableService(DiningTableRepository repository, RestaurantAccess restaurantAccess, Clock clock) {
        this.repository = repository;
        this.restaurantAccess = restaurantAccess;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DiningTableResponse> list(UUID ownerId, UUID restaurantId) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        return repository.findByRestaurantIdOrderByLabelAsc(restaurantId).stream().map(DiningTableResponse::from).toList();
    }

    @Transactional
    public DiningTableResponse create(UUID ownerId, UUID restaurantId, DiningTableRequest request) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        String label = request.label().strip();
        requireFreeLabel(restaurantId, label);
        return DiningTableResponse.from(repository.saveAndFlush(
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
        return DiningTableResponse.from(table);
    }

    /** Invalidates the old QR code (e.g. after a printed code was photographed and misused). */
    @Transactional
    public DiningTableResponse regenerateQrToken(UUID ownerId, UUID restaurantId, UUID tableId) {
        DiningTable table = requireTable(ownerId, restaurantId, tableId);
        table.replaceQrToken(SecureTokens.random(QR_TOKEN_BYTES));
        return DiningTableResponse.from(table);
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

    private void requireFreeLabel(UUID restaurantId, String label) {
        if (repository.existsByRestaurantIdAndLabelIgnoreCase(restaurantId, label)) {
            throw new ApiException(HttpStatus.CONFLICT, "A table labelled '" + label + "' already exists");
        }
    }
}
