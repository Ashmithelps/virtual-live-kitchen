package com.virtualkitchen.menu;

import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.common.storage.ObjectStorage;
import com.virtualkitchen.menu.dto.MenuCategoryRequest;
import com.virtualkitchen.menu.dto.MenuCategoryResponse;
import com.virtualkitchen.menu.dto.MenuItemRequest;
import com.virtualkitchen.menu.dto.MenuItemResponse;
import com.virtualkitchen.restaurant.RestaurantAccess;
import com.virtualkitchen.restaurant.media.MediaKeys;
import com.virtualkitchen.restaurant.media.UploadPurpose;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Every owner method first checks the restaurant belongs to the caller, then looks rows up by (id, restaurantId). */
@Service
public class MenuService {

    private final MenuCategoryRepository categories;
    private final MenuItemRepository items;
    private final RestaurantAccess restaurantAccess;
    private final ObjectStorage objectStorage;
    private final Clock clock;

    public MenuService(MenuCategoryRepository categories, MenuItemRepository items, RestaurantAccess restaurantAccess,
                       ObjectStorage objectStorage, Clock clock) {
        this.categories = categories;
        this.items = items;
        this.restaurantAccess = restaurantAccess;
        this.objectStorage = objectStorage;
        this.clock = clock;
    }

    // ── read ─────────────────────────────────────────────────────────────────

    /** Owner view: every category, including empty ones. */
    @Transactional(readOnly = true)
    public List<MenuCategoryResponse> ownerMenu(UUID ownerId, UUID restaurantId) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        return buildMenu(restaurantId, true);
    }

    /** Customer view: empty categories hidden; unavailable items shown with {@code available=false}. */
    @Transactional(readOnly = true)
    public List<MenuCategoryResponse> customerMenu(UUID restaurantId) {
        return buildMenu(restaurantId, false);
    }

    private List<MenuCategoryResponse> buildMenu(UUID restaurantId, boolean includeEmptyCategories) {
        Map<UUID, List<MenuItemResponse>> itemsByCategory = items.findByRestaurantIdOrderBySortOrderAscNameAsc(restaurantId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.groupingBy(MenuItemResponse::categoryId));
        return categories.findByRestaurantIdOrderBySortOrderAscNameAsc(restaurantId).stream()
                .map(c -> new MenuCategoryResponse(c.getId(), c.getName(), c.getDescription(), c.getSortOrder(),
                        itemsByCategory.getOrDefault(c.getId(), List.of())))
                .filter(c -> includeEmptyCategories || !c.items().isEmpty())
                .toList();
    }

    // ── categories ───────────────────────────────────────────────────────────

    @Transactional
    public MenuCategoryResponse createCategory(UUID ownerId, UUID restaurantId, MenuCategoryRequest request) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        String name = request.name().strip();
        requireFreeCategoryName(restaurantId, name);
        MenuCategory category = new MenuCategory(restaurantId, clock.instant());
        category.update(name, blankToNull(request.description()), orZero(request.sortOrder()));
        categories.saveAndFlush(category);
        return new MenuCategoryResponse(category.getId(), category.getName(), category.getDescription(),
                category.getSortOrder(), List.of());
    }

    @Transactional
    public MenuCategoryResponse updateCategory(UUID ownerId, UUID restaurantId, UUID categoryId, MenuCategoryRequest request) {
        MenuCategory category = requireCategory(ownerId, restaurantId, categoryId);
        String name = request.name().strip();
        if (!name.equalsIgnoreCase(category.getName())) {
            requireFreeCategoryName(restaurantId, name);
        }
        category.update(name, blankToNull(request.description()), orZero(request.sortOrder()));
        List<MenuItemResponse> categoryItems = items.findByRestaurantIdOrderBySortOrderAscNameAsc(restaurantId).stream()
                .filter(i -> i.getCategoryId().equals(categoryId))
                .map(this::toResponse)
                .toList();
        return new MenuCategoryResponse(category.getId(), category.getName(), category.getDescription(),
                category.getSortOrder(), categoryItems);
    }

    /** Refuses non-empty categories so items are never deleted as a side effect. */
    @Transactional
    public void deleteCategory(UUID ownerId, UUID restaurantId, UUID categoryId) {
        MenuCategory category = requireCategory(ownerId, restaurantId, categoryId);
        if (items.existsByCategoryId(categoryId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Category still has items; move or delete them first");
        }
        categories.delete(category);
    }

    // ── items ────────────────────────────────────────────────────────────────

    @Transactional
    public MenuItemResponse createItem(UUID ownerId, UUID restaurantId, MenuItemRequest request) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        MenuItem item = new MenuItem(restaurantId, clock.instant());
        apply(item, restaurantId, request, true);
        return toResponse(items.saveAndFlush(item));
    }

    @Transactional
    public MenuItemResponse updateItem(UUID ownerId, UUID restaurantId, UUID itemId, MenuItemRequest request) {
        MenuItem item = requireItem(ownerId, restaurantId, itemId);
        apply(item, restaurantId, request, item.isAvailable());
        return toResponse(items.saveAndFlush(item));
    }

    /** Quick "sold out" toggle for the kitchen during service. */
    @Transactional
    public MenuItemResponse setAvailability(UUID ownerId, UUID restaurantId, UUID itemId, boolean available) {
        MenuItem item = requireItem(ownerId, restaurantId, itemId);
        item.setAvailable(available, clock.instant());
        return toResponse(item);
    }

    @Transactional
    public void deleteItem(UUID ownerId, UUID restaurantId, UUID itemId) {
        items.delete(requireItem(ownerId, restaurantId, itemId));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void apply(MenuItem item, UUID restaurantId, MenuItemRequest request, boolean currentAvailability) {
        // The category must belong to the same restaurant (also enforced by a composite FK).
        categories.findByIdAndRestaurantId(request.categoryId(), restaurantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "categoryId does not belong to this restaurant"));
        String imageKey = MediaKeys.requireValid(blankToNull(request.imageKey()), restaurantId, UploadPurpose.MENU_ITEM_IMAGE);
        boolean available = request.available() != null ? request.available() : currentAvailability;
        Instant now = clock.instant();
        item.update(request.categoryId(), request.name().strip(), blankToNull(request.description()),
                request.pricePaise(), request.veg(), imageKey, available, orZero(request.sortOrder()), now);
    }

    private MenuCategory requireCategory(UUID ownerId, UUID restaurantId, UUID categoryId) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        return categories.findByIdAndRestaurantId(categoryId, restaurantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Menu category not found"));
    }

    private MenuItem requireItem(UUID ownerId, UUID restaurantId, UUID itemId) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        return items.findByIdAndRestaurantId(itemId, restaurantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Menu item not found"));
    }

    private void requireFreeCategoryName(UUID restaurantId, String name) {
        if (categories.existsByRestaurantIdAndNameIgnoreCase(restaurantId, name)) {
            throw new ApiException(HttpStatus.CONFLICT, "A category named '" + name + "' already exists");
        }
    }

    private MenuItemResponse toResponse(MenuItem item) {
        return new MenuItemResponse(item.getId(), item.getCategoryId(), item.getName(), item.getDescription(),
                item.getPricePaise(), item.isVeg(), item.getImageKey(), objectStorage.downloadUrl(item.getImageKey()),
                item.isAvailable(), item.getSortOrder());
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
