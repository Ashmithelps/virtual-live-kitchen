package com.virtualkitchen.billing;

import com.virtualkitchen.billing.dto.InvoiceResponse;
import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.restaurant.Restaurant;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** GST tax invoices: one per successful charge, numbered gap-free per Indian financial year (April–March, IST). */
@Service
public class InvoiceService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final JdbcTemplate jdbc;
    private final BillingProperties properties;
    private final InvoicePdfRenderer renderer;
    private final Clock clock;

    public InvoiceService(JdbcTemplate jdbc, BillingProperties properties, InvoicePdfRenderer renderer, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.renderer = renderer;
        this.clock = clock;
    }

    static String financialYear(Instant at) {
        LocalDate date = at.atZone(IST).toLocalDate();
        int start = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return start + "-" + String.format("%02d", (start + 1) % 100);
    }

    /**
     * Issues the invoice for a charge (idempotent per gateway payment: a redelivered webhook returns false).
     * Must run inside the caller's transaction so the number is only used if the charge is recorded.
     */
    @Transactional
    public boolean issue(Restaurant restaurant, Plan plan, String paymentId, long totalPaise, Instant periodStart, Instant periodEnd) {
        if (paymentId != null && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM invoices WHERE provider_payment_id = ?)",
                Boolean.class, paymentId))) {
            return false;
        }
        Instant now = clock.instant();
        String fy = financialYear(now);
        Integer n = jdbc.queryForObject("""
                INSERT INTO invoice_counters (financial_year, last_number) VALUES (?, 1)
                ON CONFLICT (financial_year) DO UPDATE SET last_number = invoice_counters.last_number + 1
                RETURNING last_number
                """, Integer.class, fy);
        String number = properties.invoicePrefix() + "/" + fy + "/" + String.format("%05d", n);
        String placeOfSupply = restaurant.getBillingStateCode() != null ? restaurant.getBillingStateCode() : properties.supplierStateCode();
        GstSplit split = GstSplit.ofTotal(totalPaise, properties.gstRateBps(), placeOfSupply.equals(properties.supplierStateCode()));
        jdbc.update("""
                INSERT INTO invoices (id, number, restaurant_id, plan_id, plan_name, period_start, period_end, taxable_paise, cgst_paise,
                                      sgst_paise, igst_paise, total_paise, gst_rate_bps, buyer_legal_name, buyer_gstin, buyer_address,
                                      place_of_supply, provider_payment_id, issued_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), number, restaurant.getId(), plan.getId(), plan.getName(), ts(periodStart), ts(periodEnd),
                split.taxablePaise(), split.cgstPaise(), split.sgstPaise(), split.igstPaise(), totalPaise, properties.gstRateBps(),
                restaurant.getLegalName() != null ? restaurant.getLegalName() : restaurant.getName(), restaurant.getGstin(),
                restaurant.getAddress() + ", " + restaurant.getCity(), placeOfSupply, paymentId, Timestamp.from(now));
        return true;
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> list(UUID restaurantId) {
        return jdbc.query("SELECT * FROM invoices WHERE restaurant_id = ? ORDER BY issued_at DESC", (rs, i) -> new InvoiceResponse(
                rs.getObject("id", UUID.class), rs.getString("number"), rs.getString("plan_name"), instant(rs.getTimestamp("period_start")),
                instant(rs.getTimestamp("period_end")), rs.getLong("taxable_paise"), rs.getLong("cgst_paise"), rs.getLong("sgst_paise"),
                rs.getLong("igst_paise"), rs.getLong("total_paise"), rs.getTimestamp("issued_at").toInstant()), restaurantId);
    }

    public record Pdf(String filename, byte[] bytes) {
    }

    @Transactional(readOnly = true)
    public Pdf pdf(UUID restaurantId, UUID invoiceId) {
        Map<String, Object> row = jdbc.query("SELECT * FROM invoices WHERE id = ? AND restaurant_id = ?", rs -> {
            if (!rs.next()) return null;
            Map<String, Object> m = new java.util.HashMap<>();
            for (int c = 1; c <= rs.getMetaData().getColumnCount(); c++) m.put(rs.getMetaData().getColumnName(c), rs.getObject(c));
            return m;
        }, invoiceId, restaurantId);
        if (row == null) throw new ApiException(HttpStatus.NOT_FOUND, "Invoice not found");
        String number = (String) row.get("number");
        InvoicePdfRenderer.Party supplier = new InvoicePdfRenderer.Party(properties.supplierLegalName(), properties.supplierGstin(),
                properties.supplierAddress(), properties.supplierStateCode());
        InvoicePdfRenderer.Party buyer = new InvoicePdfRenderer.Party((String) row.get("buyer_legal_name"), (String) row.get("buyer_gstin"),
                (String) row.get("buyer_address"), (String) row.get("place_of_supply"));
        byte[] bytes = renderer.render(new InvoicePdfRenderer.Data(number, ((Timestamp) row.get("issued_at")).toInstant(), supplier, buyer,
                (String) row.get("place_of_supply"), properties.sacCode(), "Virtual Live Kitchen subscription: " + row.get("plan_name") + " plan (monthly)",
                instant((Timestamp) row.get("period_start")), instant((Timestamp) row.get("period_end")), (Long) row.get("taxable_paise"),
                (Long) row.get("cgst_paise"), (Long) row.get("sgst_paise"), (Long) row.get("igst_paise"), (Long) row.get("total_paise"),
                (Integer) row.get("gst_rate_bps"), (String) row.get("provider_payment_id")));
        return new Pdf(number.replace('/', '-') + ".pdf", bytes);
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
