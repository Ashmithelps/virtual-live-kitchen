package com.virtualkitchen.admin.dto;

import com.virtualkitchen.auth.dto.UserResponse;
import com.virtualkitchen.billing.dto.AdminSubscriptionResponse;
import com.virtualkitchen.billing.dto.InvoiceResponse;
import com.virtualkitchen.restaurant.dto.ComplianceResponse;
import com.virtualkitchen.restaurant.dto.RestaurantResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Everything an admin needs to approve or suspend a restaurant: owner, licence documents, billing. */
public record AdminRestaurantDetailResponse(RestaurantResponse restaurant, UserResponse owner, ComplianceResponse compliance,
                                            @Schema(nullable = true) AdminSubscriptionResponse subscription, List<InvoiceResponse> invoices) {
}
