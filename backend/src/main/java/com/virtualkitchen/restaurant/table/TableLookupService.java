package com.virtualkitchen.restaurant.table;

import com.virtualkitchen.auth.token.ScopedTokens;
import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.menu.MenuService;
import com.virtualkitchen.restaurant.Restaurant;
import com.virtualkitchen.restaurant.RestaurantRepository;
import com.virtualkitchen.restaurant.RestaurantStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TableLookupService {

    private final DiningTableRepository tables;
    private final RestaurantRepository restaurants;
    private final MenuService menuService;
    private final TableSessions sessions;

    public TableLookupService(DiningTableRepository tables, RestaurantRepository restaurants, MenuService menuService,
                              TableSessions sessions) {
        this.tables = tables;
        this.restaurants = restaurants;
        this.menuService = menuService;
        this.sessions = sessions;
    }

    /** Unknown or regenerated tokens, and tables of non-ACTIVE restaurants, are all 404. */
    @Transactional(readOnly = true)
    public TableLookupResponse lookup(String qrToken) {
        DiningTable table = tables.findByQrToken(qrToken)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Table not found"));
        Restaurant restaurant = restaurants.findById(table.getRestaurantId())
                .filter(r -> r.getStatus() == RestaurantStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Table not found"));
        ScopedTokens.Issued session = sessions.start(table);
        return new TableLookupResponse(restaurant.getId(), restaurant.getSlug(), restaurant.getName(), table.getLabel(),
                session.token(), session.expiresAt(), menuService.customerMenu(restaurant.getId()));
    }
}
