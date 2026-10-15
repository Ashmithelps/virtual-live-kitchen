package com.virtualkitchen.billing.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChoosePlanRequest(@NotNull UUID planId) {
}
