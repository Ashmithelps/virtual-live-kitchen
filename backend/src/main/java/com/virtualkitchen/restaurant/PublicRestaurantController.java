package com.virtualkitchen.restaurant;

import com.virtualkitchen.common.web.PageResponse;
import com.virtualkitchen.restaurant.dto.RestaurantDetailResponse;
import com.virtualkitchen.restaurant.dto.RestaurantSummaryResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Unauthenticated customer-facing endpoints. Only ACTIVE restaurants are visible. */
@RestController
@RequestMapping("/api/restaurants")
public class PublicRestaurantController {

    private final RestaurantSearchService searchService;
    private final RestaurantService restaurantService;

    public PublicRestaurantController(RestaurantSearchService searchService, RestaurantService restaurantService) {
        this.searchService = searchService;
        this.restaurantService = restaurantService;
    }

    @GetMapping
    public PageResponse<RestaurantSummaryResponse> list(
            @RequestParam @NotBlank String city,
            @RequestParam(required = false) String cuisine,
            @RequestParam(defaultValue = "false") boolean vegOnly,
            @RequestParam(defaultValue = "false") boolean openNow,
            @RequestParam(required = false) OrderType orderType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return searchService.search(
                new RestaurantSearchService.Filter(city, cuisine, vegOnly, openNow, orderType), page, size);
    }

    @GetMapping("/{slug}")
    public RestaurantDetailResponse detail(@PathVariable String slug) {
        return restaurantService.getPublicBySlug(slug);
    }
}
