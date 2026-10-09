package com.virtualkitchen.admin;

import com.virtualkitchen.restaurant.RestaurantService;
import com.virtualkitchen.restaurant.RestaurantStatus;
import com.virtualkitchen.restaurant.dto.RestaurantResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/restaurants")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRestaurantController {

    private final RestaurantService restaurantService;

    public AdminRestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @GetMapping
    public List<RestaurantResponse> list(@RequestParam(defaultValue = "PENDING_APPROVAL") RestaurantStatus status) {
        return restaurantService.listByStatus(status);
    }

    @PostMapping("/{restaurantId}/approve")
    public RestaurantResponse approve(@PathVariable UUID restaurantId) {
        return restaurantService.changeStatusAsAdmin(restaurantId, RestaurantStatus.ACTIVE);
    }

    @PostMapping("/{restaurantId}/suspend")
    public RestaurantResponse suspend(@PathVariable UUID restaurantId) {
        return restaurantService.changeStatusAsAdmin(restaurantId, RestaurantStatus.SUSPENDED);
    }
}
