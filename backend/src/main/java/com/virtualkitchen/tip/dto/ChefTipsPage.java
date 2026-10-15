package com.virtualkitchen.tip.dto;

import java.util.List;

/** Paid and transferred tips, newest first, plus lifetime totals. */
public record ChefTipsPage(List<ChefTipResponse> items, int page, int size, long totalItems, int totalPages, long tipCount,
                           long totalChefAmountPaise) {
}
