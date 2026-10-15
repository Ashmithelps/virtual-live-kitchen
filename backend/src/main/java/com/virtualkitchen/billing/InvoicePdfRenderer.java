package com.virtualkitchen.billing;

import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** A GST tax invoice as PDF (Helvetica, so amounts are written "INR 1,178.82" rather than with the rupee sign). */
@Component
class InvoicePdfRenderer {

    record Party(String legalName, String gstin, String address, String stateCode) {
    }

    record Data(String number, Instant issuedAt, Party supplier, Party buyer, String placeOfSupply, String sacCode, String description,
                Instant periodStart, Instant periodEnd, long taxablePaise, long cgstPaise, long sgstPaise, long igstPaise, long totalPaise,
                int gstRateBps, String paymentReference) {
    }

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(IST);
    private static final Font TITLE = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font BOLD = new Font(Font.HELVETICA, 9, Font.BOLD);
    private static final Font NORMAL = new Font(Font.HELVETICA, 9);
    private static final Font SMALL = new Font(Font.HELVETICA, 7.5f);

    byte[] render(Data d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        try {
            PdfWriter.getInstance(document, out);
            document.addTitle("Tax invoice " + d.number());
            document.open();

            Paragraph title = new Paragraph("TAX INVOICE", TITLE);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" ", SMALL));

            PdfPTable head = new PdfPTable(2);
            head.setWidthPercentage(100);
            head.addCell(party("Supplier", d.supplier()));
            head.addCell(meta(d));
            head.addCell(party("Bill to", d.buyer()));
            PdfPCell supply = cell("Place of supply: " + d.placeOfSupply() + "\nReverse charge: No", NORMAL);
            head.addCell(supply);
            document.add(head);
            document.add(new Paragraph(" ", SMALL));

            boolean intra = d.igstPaise() == 0;
            String half = pct(d.gstRateBps() / 2);
            PdfPTable lines = new PdfPTable(new float[]{4.5f, 1.2f, 2f});
            lines.setWidthPercentage(100);
            lines.addCell(cell("Description", BOLD));
            lines.addCell(cell("SAC", BOLD));
            lines.addCell(right("Amount (INR)", BOLD));
            String period = d.periodStart() != null && d.periodEnd() != null
                    ? "\nService period " + DATE.format(d.periodStart()) + " – " + DATE.format(d.periodEnd()) : "";
            lines.addCell(cell(d.description() + period, NORMAL));
            lines.addCell(cell(d.sacCode(), NORMAL));
            lines.addCell(right(money(d.taxablePaise()), NORMAL));
            totalRow(lines, "Taxable value", d.taxablePaise(), NORMAL);
            if (intra) {
                totalRow(lines, "CGST @ " + half, d.cgstPaise(), NORMAL);
                totalRow(lines, "SGST @ " + half, d.sgstPaise(), NORMAL);
            } else {
                totalRow(lines, "IGST @ " + pct(d.gstRateBps()), d.igstPaise(), NORMAL);
            }
            totalRow(lines, "Total (INR)", d.totalPaise(), BOLD);
            document.add(lines);

            document.add(new Paragraph(" ", SMALL));
            document.add(new Paragraph("Paid by recurring payment" + (d.paymentReference() != null ? " (ref " + d.paymentReference() + ")" : "")
                    + ". Amounts in Indian rupees.", SMALL));
            document.add(new Paragraph("This is a computer-generated invoice and needs no signature.", SMALL));
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not render invoice " + d.number(), e);
        }
        return out.toByteArray();
    }

    private static PdfPCell party(String heading, Party p) {
        Phrase phrase = new Phrase();
        phrase.add(new Chunk(heading + "\n", BOLD));
        phrase.add(new Chunk(p.legalName() + "\n" + p.address() + "\n", NORMAL));
        phrase.add(new Chunk("GSTIN: " + (p.gstin() == null || p.gstin().isBlank() ? "Unregistered" : p.gstin()) + "\n", NORMAL));
        phrase.add(new Chunk("State code: " + p.stateCode(), NORMAL));
        PdfPCell cell = new PdfPCell(phrase);
        cell.setPadding(6);
        return cell;
    }

    private static PdfPCell meta(Data d) {
        return cell("Invoice no: " + d.number() + "\nInvoice date: " + DATE.format(d.issuedAt()), NORMAL);
    }

    private static void totalRow(PdfPTable table, String label, long paise, Font font) {
        PdfPCell l = cell(label, font);
        l.setColspan(2);
        l.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(l);
        table.addCell(right(money(paise), font));
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        cell.setBorder(Rectangle.BOX);
        return cell;
    }

    private static PdfPCell right(String text, Font font) {
        PdfPCell cell = cell(text, font);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    /** Indian digit grouping (12,34,567.89) from integer paise, no floating point. */
    static String money(long paise) {
        String sign = paise < 0 ? "-" : "";
        long abs = Math.abs(paise);
        String rupees = Long.toString(abs / 100);
        String grouped;
        if (rupees.length() <= 3) {
            grouped = rupees;
        } else {
            String head = rupees.substring(0, rupees.length() - 3);
            StringBuilder out = new StringBuilder();
            for (int i = head.length(); i > 0; i -= 2) {
                out.insert(0, head.substring(Math.max(0, i - 2), i) + ",");
            }
            grouped = out + rupees.substring(rupees.length() - 3);
        }
        return sign + grouped + "." + String.format("%02d", abs % 100);
    }

    private static String pct(int bps) {
        return (bps % 100 == 0 ? String.valueOf(bps / 100) : String.valueOf(bps / 100.0)) + "%";
    }
}
