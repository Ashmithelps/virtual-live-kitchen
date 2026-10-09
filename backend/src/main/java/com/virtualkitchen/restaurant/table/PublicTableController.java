package com.virtualkitchen.restaurant.table;

import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.restaurant.RestaurantRepository;
import com.virtualkitchen.restaurant.RestaurantStatus;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Resolves a scanned QR token; the token itself is then sent with a dine-in order. */
@RestController
public class PublicTableController {

    private final DiningTableRepository tables;
    private final RestaurantRepository restaurants;

    public PublicTableController(DiningTableRepository tables, RestaurantRepository restaurants) {
        this.tables = tables;
        this.restaurants = restaurants;
    }

    @GetMapping("/api/tables/{qrToken}")
    @Transactional(readOnly = true)
    public TableLookupResponse lookup(@PathVariable String qrToken) {
        DiningTable table = tables.findByQrToken(qrToken)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Table not found"));
        var restaurant = restaurants.findById(table.getRestaurantId())
                .filter(r -> r.getStatus() == RestaurantStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Table not found"));
        return new TableLookupResponse(restaurant.getId(), restaurant.getSlug(), restaurant.getName(), table.getLabel());
    }
}
