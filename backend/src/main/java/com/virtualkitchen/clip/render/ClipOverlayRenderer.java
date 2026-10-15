package com.virtualkitchen.clip.render;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Draws the clip's overlay as a transparent 1280×720 PNG that FFmpeg composites on every frame.
 * Doing text in Java2D instead of FFmpeg's drawtext works with any FFmpeg build (drawtext needs
 * libfreetype) and gives proper font fallback; the server only needs a TTF font installed.
 */
@Component
public class ClipOverlayRenderer {

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    /** @param logo optional, already decoded */
    public record Overlay(String restaurantName, BufferedImage logo, int displayNumber, String chefName, String date, String watermark) {
    }

    public void render(Overlay overlay, Path target) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

            // Bottom band: transparent → 75% black, so text is readable over any kitchen footage.
            int band = 190;
            g.setPaint(new GradientPaint(0, HEIGHT - band, new Color(0, 0, 0, 0), 0, HEIGHT, new Color(0, 0, 0, 190)));
            g.fillRect(0, HEIGHT - band, WIDTH, band);

            int x = 40;
            int logoSize = 96;
            if (overlay.logo() != null) {
                int top = HEIGHT - 40 - logoSize;
                g.setColor(Color.WHITE);
                g.fill(new RoundRectangle2D.Float(x - 4, top - 4, logoSize + 8, logoSize + 8, 24, 24));
                drawFitted(g, overlay.logo(), x, top, logoSize);
                x += logoSize + 24;
            }
            int maxText = WIDTH - x - 40;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 40));
            drawText(g, ellipsize(g, overlay.restaurantName(), maxText), x, HEIGHT - 92, Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28));
            String details = "Order #" + overlay.displayNumber()
                    + (overlay.chefName() == null || overlay.chefName().isBlank() ? "" : "  ·  " + chefLabel(overlay.chefName()))
                    + "  ·  " + overlay.date();
            drawText(g, ellipsize(g, details, maxText), x, HEIGHT - 48, new Color(255, 255, 255, 235));

            // Platform watermark, top-right, subtle.
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            FontMetrics fm = g.getFontMetrics();
            String mark = overlay.watermark();
            drawText(g, mark, WIDTH - 32 - fm.stringWidth(mark), 48, new Color(255, 255, 255, 230));
        } finally {
            g.dispose();
        }
        try {
            ImageIO.write(image, "png", target.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void drawText(Graphics2D g, String text, int x, int y, Color color) {
        g.setColor(new Color(0, 0, 0, 140)); // soft shadow
        g.drawString(text, x + 2, y + 2);
        g.setColor(color);
        g.drawString(text, x, y);
    }

    private static void drawFitted(Graphics2D g, BufferedImage logo, int x, int y, int box) {
        double scale = Math.min((double) box / logo.getWidth(), (double) box / logo.getHeight());
        int w = (int) Math.round(logo.getWidth() * scale);
        int h = (int) Math.round(logo.getHeight() * scale);
        g.setComposite(AlphaComposite.SrcOver);
        g.drawImage(logo, x + (box - w) / 2, y + (box - h) / 2, w, h, null);
    }

    static String ellipsize(Graphics2D g, String text, int maxWidth) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && fm.stringWidth(cut + "…") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.strip() + "…";
    }

    /** "Ravi" → "Chef Ravi"; a display name that already says "Chef …" is kept as is. */
    static String chefLabel(String displayName) {
        String name = displayName.strip();
        return name.regionMatches(true, 0, "chef ", 0, 5) ? name : "Chef " + name;
    }
}
