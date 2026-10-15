package com.virtualkitchen.payment.razorpay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Business profile sent when creating a chef's Route linked account (Razorpay needs a category/subcategory from its
 * published list; check it against your account if creation is rejected).
 */
@ConfigurationProperties(prefix = "app.payment.razorpay.route")
public record RazorpayRouteProperties(String businessCategory, String businessSubcategory) {
}
