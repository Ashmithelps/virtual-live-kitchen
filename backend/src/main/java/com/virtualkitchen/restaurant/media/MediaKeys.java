package com.virtualkitchen.restaurant.media;

import com.virtualkitchen.common.error.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Object keys look like {@code restaurants/<restaurantId>/<folder>/<uuid>.<ext>}. Keys are minted
 * by the server; when an owner later attaches a key to a menu item or chef, it must match this
 * shape for the same restaurant, so nobody can point their menu at another restaurant's objects.
 */
public final class MediaKeys {

    static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private MediaKeys() {
    }

    static String newKey(UUID restaurantId, UploadPurpose purpose, String contentType) {
        String extension = purpose.allowsPdf() && "application/pdf".equals(contentType) ? "pdf" : EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "contentType must be one of " + EXTENSIONS.keySet()
                    + (purpose.allowsPdf() ? " or application/pdf" : ""));
        }
        return "restaurants/" + restaurantId + "/" + purpose.folder() + "/" + UUID.randomUUID() + "." + extension;
    }

    /** @return {@code key} unchanged (null allowed, meaning "no image") */
    public static String requireValid(String key, UUID restaurantId, UploadPurpose purpose) {
        if (key == null) {
            return null;
        }
        Pattern expected = Pattern.compile("^restaurants/" + restaurantId + "/" + purpose.folder()
                + "/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp" + (purpose.allowsPdf() ? "|pdf" : "") + ")$");
        if (!expected.matcher(key).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Image key must come from POST /api/owner/restaurants/" + restaurantId + "/uploads with purpose " + purpose);
        }
        return key;
    }
}
