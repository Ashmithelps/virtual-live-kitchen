package com.virtualkitchen.restaurant.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Licence and tax details. FSSAI licence numbers are 14 digits; the document key comes from an upload with purpose
 * FSSAI_LICENCE. GSTIN is optional (unregistered restaurants); the state code decides CGST+SGST vs IGST on invoices
 * and must match the GSTIN's first two digits when both are given.
 */
public record ComplianceRequest(
        @Pattern(regexp = "^[0-9]{14}$", message = "must be the 14-digit FSSAI licence number") String fssaiNumber,
        @Size(max = 300) String fssaiDocKey,
        @Size(min = 2, max = 160) String legalName,
        @Pattern(regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$", message = "must be a valid 15-character GSTIN") String gstin,
        @Pattern(regexp = "^[0-9]{2}$", message = "must be the 2-digit GST state code, e.g. 29 for Karnataka") String billingStateCode) {
}
