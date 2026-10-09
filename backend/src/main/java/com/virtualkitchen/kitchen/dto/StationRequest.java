package com.virtualkitchen.kitchen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StationRequest(@NotBlank @Size(max = 60) String name) {
}
