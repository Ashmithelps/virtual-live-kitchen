package com.virtualkitchen.clip.render;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ClipOverlayRendererTest {

    @TempDir Path dir;

    @Test
    void transparentFrameWithBottomBandLogoAndWatermark() throws Exception {
        BufferedImage logo = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        var g = logo.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 200, 100);
        g.dispose();
        Path png = dir.resolve("o.png");

        new ClipOverlayRenderer().render(new ClipOverlayRenderer.Overlay(
                "A Restaurant With An Extremely Long Name That Would Never Fit On One Line Of A 720p Frame", logo, 142, "Ravi",
                "11 Oct 2026", "Virtual Live Kitchen"), png);

        BufferedImage image = ImageIO.read(png.toFile());
        assertThat(image.getWidth()).isEqualTo(1280);
        assertThat(image.getHeight()).isEqualTo(720);
        assertThat(alpha(image.getRGB(640, 300))).as("middle is see-through").isZero();
        assertThat(alpha(image.getRGB(10, 715))).as("bottom band darkens").isGreaterThan(150);
        assertThat(image.getRGB(80, 640) & 0xFFFFFF).as("logo drawn bottom-left").isEqualTo(0xFF0000);
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    @Test
    void chefLabelDoesNotRepeatTheTitle() {
        assertThat(ClipOverlayRenderer.chefLabel("Ravi")).isEqualTo("Chef Ravi");
        assertThat(ClipOverlayRenderer.chefLabel("Chef Ravi")).isEqualTo("Chef Ravi");
        assertThat(ClipOverlayRenderer.chefLabel(" chef meera ")).isEqualTo("chef meera");
    }
}
