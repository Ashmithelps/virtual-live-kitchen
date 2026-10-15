package com.virtualkitchen.tip.dto;

import java.util.List;

/** Who can be tipped on an order (only chefs whose payouts are ACTIVE), tips already made, and the amount limits. */
public record OrderTipsResponse(List<TippableChef> chefs, List<TipResponse> tips, List<Long> presetsPaise, long minPaise,
                                long maxPaise) {
}
