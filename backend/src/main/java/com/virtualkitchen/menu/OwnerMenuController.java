package com.virtualkitchen.menu;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.menu.dto.AvailabilityRequest;
import com.virtualkitchen.menu.dto.MenuCategoryRequest;
import com.virtualkitchen.menu.dto.MenuCategoryResponse;
import com.virtualkitchen.menu.dto.MenuItemRequest;
import com.virtualkitchen.menu.dto.MenuItemResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/owner/restaurants/{restaurantId}/menu")
@PreAuthorize("hasRole('RESTAURANT_OWNER')")
public class OwnerMenuController {

    private final MenuService menuService;

    public OwnerMenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public List<MenuCategoryResponse> menu(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId) {
        return menuService.ownerMenu(owner.id(), restaurantId);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuCategoryResponse createCategory(@AuthenticationPrincipal AuthenticatedUser owner,
                                               @PathVariable UUID restaurantId,
                                               @Valid @RequestBody MenuCategoryRequest request) {
        return menuService.createCategory(owner.id(), restaurantId, request);
    }

    @PutMapping("/categories/{categoryId}")
    public MenuCategoryResponse updateCategory(@AuthenticationPrincipal AuthenticatedUser owner,
                                               @PathVariable UUID restaurantId, @PathVariable UUID categoryId,
                                               @Valid @RequestBody MenuCategoryRequest request) {
        return menuService.updateCategory(owner.id(), restaurantId, categoryId, request);
    }

    @DeleteMapping("/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@AuthenticationPrincipal AuthenticatedUser owner,
                               @PathVariable UUID restaurantId, @PathVariable UUID categoryId) {
        menuService.deleteCategory(owner.id(), restaurantId, categoryId);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuItemResponse createItem(@AuthenticationPrincipal AuthenticatedUser owner,
                                       @PathVariable UUID restaurantId,
                                       @Valid @RequestBody MenuItemRequest request) {
        return menuService.createItem(owner.id(), restaurantId, request);
    }

    @PutMapping("/items/{itemId}")
    public MenuItemResponse updateItem(@AuthenticationPrincipal AuthenticatedUser owner,
                                       @PathVariable UUID restaurantId, @PathVariable UUID itemId,
                                       @Valid @RequestBody MenuItemRequest request) {
        return menuService.updateItem(owner.id(), restaurantId, itemId, request);
    }

    @PatchMapping("/items/{itemId}/availability")
    public MenuItemResponse setAvailability(@AuthenticationPrincipal AuthenticatedUser owner,
                                            @PathVariable UUID restaurantId, @PathVariable UUID itemId,
                                            @Valid @RequestBody AvailabilityRequest request) {
        return menuService.setAvailability(owner.id(), restaurantId, itemId, request.available());
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@AuthenticationPrincipal AuthenticatedUser owner,
                           @PathVariable UUID restaurantId, @PathVariable UUID itemId) {
        menuService.deleteItem(owner.id(), restaurantId, itemId);
    }
}
