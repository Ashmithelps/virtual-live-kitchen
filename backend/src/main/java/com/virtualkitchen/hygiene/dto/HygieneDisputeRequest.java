package com.virtualkitchen.hygiene.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HygieneDisputeRequest(@NotBlank @Size(min = 10, max = 1000) String reason) {
}
