package com.virtualkitchen.restaurant.table;

import com.google.zxing.common.BitMatrix;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.PageSize;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * A4 PDF, one table per page: restaurant name, big table label, the QR code drawn as vector
 * squares (sharp at any print size), and the URL as a fallback for people without a scanner.
 *
 * <p>Uses the built-in Helvetica (WinAnsi), so names outside Latin-1 (e.g. Devanagari) won't
 * render; embedding a Unicode font is a follow-up.
 */
@Component
public class TableQrSheetRenderer {

    public record TableQr(String label, String url) {
    }

    private static final float QR_SIZE = 380f;          // ~13.4 cm
    private static final float MAX_TEXT_WIDTH = 500f;

    public byte[] render(String restaurantName, List<TableQr> tables) {
        Rectangle page = PageSize.A4;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(page);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.addTitle(restaurantName + " — table QR codes");
            document.open();
            BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            BaseFont regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            float centerX = page.getWidth() / 2;

            for (int i = 0; i < tables.size(); i++) {
                if (i > 0) {
                    document.newPage();
                }
                TableQr table = tables.get(i);
                PdfContentByte canvas = writer.getDirectContent();
                // Leaves well over the 4-module quiet zone the QR spec asks for on every side.
                text(canvas, bold, fitSize(bold, restaurantName, 28), restaurantName, centerX, 740);
                text(canvas, bold, fitSize(bold, "Table " + table.label(), 54), "Table " + table.label(), centerX, 670);
                drawQr(canvas, QrCodes.encode(table.url()), centerX - QR_SIZE / 2, 220, QR_SIZE);
                text(canvas, regular, 18, "Scan to see the menu and order", centerX, 175);
                text(canvas, regular, fitSize(regular, table.url(), 9), table.url(), centerX, 150);
            }
            document.close();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Could not render QR sheet", e);
        }
        return out.toByteArray();
    }

    private static void drawQr(PdfContentByte canvas, BitMatrix matrix, float x, float y, float size) {
        float module = size / matrix.getWidth();
        canvas.saveState();
        canvas.setRGBColorFill(0, 0, 0);
        for (int row = 0; row < matrix.getHeight(); row++) {
            for (int col = 0; col < matrix.getWidth(); col++) {
                if (matrix.get(col, row)) {
                    // PDF y grows upwards; QR row 0 is the top.
                    canvas.rectangle(x + col * module, y + size - (row + 1) * module, module, module);
                }
            }
        }
        canvas.fill();
        canvas.restoreState();
    }

    private static void text(PdfContentByte canvas, BaseFont font, float size, String text, float centerX, float y) {
        canvas.beginText();
        canvas.setFontAndSize(font, size);
        canvas.showTextAligned(PdfContentByte.ALIGN_CENTER, text, centerX, y, 0);
        canvas.endText();
    }

    /** Shrinks long names so they stay on the page. */
    private static float fitSize(BaseFont font, String text, float preferred) {
        float width = font.getWidthPoint(text, preferred);
        return width <= MAX_TEXT_WIDTH ? preferred : preferred * MAX_TEXT_WIDTH / width;
    }
}
