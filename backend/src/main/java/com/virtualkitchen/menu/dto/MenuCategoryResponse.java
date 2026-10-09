package com.virtualkitchen.menu.dto;

import java.util.List;
import java.util.UUID;

public record MenuCategoryResponse(UUID id, String name, String description, int sortOrder, List<MenuItemResponse> items) {
}
