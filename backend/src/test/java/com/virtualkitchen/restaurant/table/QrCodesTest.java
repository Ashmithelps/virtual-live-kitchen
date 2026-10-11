package com.virtualkitchen.restaurant.table;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The matrix drawn into the PDF must decode back to the table URL. */
class QrCodesTest {

    @Test
    void encodedMatrixDecodesToTheUrl() throws Exception {
        String url = "https://order.example.com/t/Xk3vLq9-aZ_0123456789abcdefghijklmnopqrstu";
        BitMatrix matrix = QrCodes.encode(url);

        assertThat(decode(matrix)).isEqualTo(url);
    }

    /** Renders the matrix at 4px per module with a 4-module quiet zone, then runs ZXing's reader. */
    private static String decode(BitMatrix matrix) throws Exception {
        int scale = 4, quiet = 4 * scale;
        int size = matrix.getWidth() * scale + 2 * quiet;
        int[] pixels = new int[size * size];
        java.util.Arrays.fill(pixels, 0xFFFFFFFF);
        for (int y = 0; y < matrix.getHeight(); y++)
            for (int x = 0; x < matrix.getWidth(); x++)
                if (matrix.get(x, y))
                    for (int dy = 0; dy < scale; dy++)
                        for (int dx = 0; dx < scale; dx++)
                            pixels[(quiet + y * scale + dy) * size + quiet + x * scale + dx] = 0xFF000000;
        LuminanceSource source = new RGBLuminanceSource(size, size, pixels);
        return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText();
    }
}
