package com.virtualkitchen.billing;

import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GstAndInvoiceTest {

    @Test
    void gstIsAddedOnThePriceAndSplitFromTheChargedTotal() {
        assertThat(GstSplit.gstOn(99_900, 1800)).isEqualTo(17_982);          // ₹999 + 18% = ₹1,178.82
        GstSplit intra = GstSplit.ofTotal(117_882, 1800, true);
        assertThat(intra).isEqualTo(new GstSplit(99_900, 8_991, 8_991, 0));
        GstSplit inter = GstSplit.ofTotal(117_882, 1800, false);
        assertThat(inter).isEqualTo(new GstSplit(99_900, 0, 0, 17_982));
        GstSplit odd = GstSplit.ofTotal(118_001, 1800, true);               // odd GST paisa goes to SGST
        assertThat(odd.total()).isEqualTo(118_001);
        assertThat(odd.sgstPaise() - odd.cgstPaise()).isBetween(0L, 1L);
    }

    @Test
    void invoiceNumbersRunPerIndianFinancialYear() {
        assertThat(InvoiceService.financialYear(Instant.parse("2026-03-31T18:00:00Z"))).isEqualTo("2025-26"); // still 31 Mar in IST
        assertThat(InvoiceService.financialYear(Instant.parse("2026-03-31T18:31:00Z"))).isEqualTo("2026-27"); // 1 Apr 00:01 IST
        assertThat(InvoiceService.financialYear(Instant.parse("2027-01-15T00:00:00Z"))).isEqualTo("2026-27");
        assertThat(InvoiceService.financialYear(Instant.parse("2099-12-01T00:00:00Z"))).isEqualTo("2099-00");
    }

    @Test
    void invoicePdfCarriesTheGstDetails() throws Exception {
        InvoicePdfRenderer.Party supplier = new InvoicePdfRenderer.Party("Virtual Live Kitchen Pvt Ltd", "29ABCDE1234F1Z5", "Bengaluru", "29");
        InvoicePdfRenderer.Party buyer = new InvoicePdfRenderer.Party("Spice Foods LLP", null, "12 MG Road, Bengaluru", "29");
        byte[] pdf = new InvoicePdfRenderer().render(new InvoicePdfRenderer.Data("VLK/2026-27/00007", Instant.parse("2026-10-15T06:00:00Z"),
                supplier, buyer, "29", "998315", "Virtual Live Kitchen subscription: Starter plan (monthly)", Instant.parse("2026-10-15T06:00:00Z"),
                Instant.parse("2026-11-15T06:00:00Z"), 99_900, 8_991, 8_991, 0, 117_882, 1800, "pay_123"));
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        String text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
        assertThat(text).contains("TAX INVOICE", "VLK/2026-27/00007", "29ABCDE1234F1Z5", "Unregistered", "998315",
                "CGST @ 9%", "SGST @ 9%", "1,178.82", "999.00", "89.91", "Place of supply: 29");
        assertThat(InvoicePdfRenderer.money(12_34_567_89L)).isEqualTo("12,34,567.89"); // Indian grouping
        assertThat(InvoicePdfRenderer.money(1_00_000_00L)).isEqualTo("1,00,000.00");
        assertThat(InvoicePdfRenderer.money(99_900)).isEqualTo("999.00");
        assertThat(InvoicePdfRenderer.money(5)).isEqualTo("0.05");
    }
}
