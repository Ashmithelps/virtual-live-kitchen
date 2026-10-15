package com.virtualkitchen.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * GST on a charge whose total already includes it (the gateway charges plan price + GST). Taxable value is derived
 * from what was actually charged; within the supplier's state the tax splits into CGST + SGST (the odd paisa goes to
 * SGST), otherwise it is all IGST. All integer paise.
 */
public record GstSplit(long taxablePaise, long cgstPaise, long sgstPaise, long igstPaise) {

    public static long gstOn(long taxablePaise, int rateBps) {
        return BigDecimal.valueOf(taxablePaise).multiply(BigDecimal.valueOf(rateBps))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP).longValueExact();
    }

    public static GstSplit ofTotal(long totalPaise, int rateBps, boolean sameState) {
        long taxable = BigDecimal.valueOf(totalPaise).multiply(BigDecimal.valueOf(10_000))
                .divide(BigDecimal.valueOf(10_000L + rateBps), 0, RoundingMode.HALF_UP).longValueExact();
        long gst = totalPaise - taxable;
        if (!sameState) {
            return new GstSplit(taxable, 0, 0, gst);
        }
        long cgst = gst / 2;
        return new GstSplit(taxable, cgst, gst - cgst, 0);
    }

    public long total() {
        return taxablePaise + cgstPaise + sgstPaise + igstPaise;
    }
}
