package com.virtualkitchen.billing.dto;

public record BillingSettingsResponse(int trialDays, int graceDays, int gstRateBps, String provider, boolean simulationAvailable) {
}
