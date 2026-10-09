package com.virtualkitchen.kitchen.consent;

import com.virtualkitchen.common.crypto.SecureTokens;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The current recording-consent text, loaded once from
 * {@code classpath:consent/recording-consent-<version>.txt}. Old version files must be kept so
 * the hash stored with each acceptance can always be matched to the exact text a chef saw.
 */
@Component
public class ConsentDocument {

    private final String version;
    private final String text;
    private final String textHash;

    public ConsentDocument(ConsentProperties properties) {
        this.version = properties.currentVersion();
        String path = "consent/recording-consent-" + version + ".txt";
        try {
            this.text = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Consent text not found at classpath:" + path, e);
        }
        this.textHash = SecureTokens.sha256Hex(text);
    }

    public String version() { return version; }
    public String text() { return text; }
    public String textHash() { return textHash; }
}
