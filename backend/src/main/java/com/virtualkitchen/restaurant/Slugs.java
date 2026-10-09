package com.virtualkitchen.restaurant;

import java.text.Normalizer;
import java.util.Locale;

public final class Slugs {

    public static final String REGEX = "^[a-z0-9]+(?:-[a-z0-9]+)*$";
    public static final String MESSAGE = "must be lower-case letters, digits and single hyphens, e.g. spice-garden-indiranagar";
    static final int MAX_LENGTH = 80;

    private Slugs() {
    }

    /** "Café Spice & Co, Bengaluru" → "cafe-spice-co-bengaluru". May return "" for non-Latin input. */
    public static String slugify(String text) {
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (slug.length() > MAX_LENGTH - 6) { // leave room for a "-xxxxx" uniqueness suffix
            slug = slug.substring(0, MAX_LENGTH - 6).replaceAll("-+$", "");
        }
        return slug;
    }
}
