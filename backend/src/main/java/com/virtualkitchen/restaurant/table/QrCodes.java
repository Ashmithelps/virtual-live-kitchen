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
