package com.virtualkitchen.restaurant.table;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.Map;

public final class QrCodes {

    private QrCodes() {
    }

    /**
     * One cell per QR module, no quiet zone (the caller leaves white space around it). Error
     * correction M survives ~15% damage — enough for a laminated card with a few scratches.
     */
    /**
     * QR code as a standalone SVG data URI (for an {@code <img src>}, which can't run scripts).
     * Horizontal runs of dark modules are merged into single rects to keep it small.
     */
    public static String svgDataUri(String text) {
        BitMatrix m = encode(text);
        int quiet = 4;
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < m.getHeight(); y++) {
            int x = 0;
            while (x < m.getWidth()) {
                if (!m.get(x, y)) {
                    x++;
                    continue;
                }
                int start = x;
                while (x < m.getWidth() && m.get(x, y)) x++;
                path.append('M').append(start + quiet).append(' ').append(y + quiet)
                        .append('h').append(x - start).append("v1h-").append(x - start).append('z');
            }
        }
        int size = m.getWidth() + 2 * quiet;
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + size + " " + size
                + "\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>"
                + "<path d=\"" + path + "\" fill=\"#000\"/></svg>";
        return "data:image/svg+xml;base64," + java.util.Base64.getEncoder().encodeToString(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static BitMatrix encode(String text) {
        try {
            return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 0,
                    EncodeHintType.CHARACTER_SET, "UTF-8"));
        } catch (WriterException e) {
            throw new IllegalArgumentException("Cannot encode QR code", e);
        }
    }
}
