package com.virtualkitchen.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record MenuCategoryResponse(UUID id, String name, @Schema(nullable = true) String description, int sortOrder, List<MenuItemResponse> items) {
}
