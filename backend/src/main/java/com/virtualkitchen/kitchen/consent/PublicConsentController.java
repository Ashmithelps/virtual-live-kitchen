package com.virtualkitchen.kitchen.consent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The current chef recording-consent text, public so the web app's legal pages can show it. */
@RestController
public class PublicConsentController {

    private final ConsentDocument document;

    public PublicConsentController(ConsentDocument document) {
        this.document = document;
    }

    public record PublicConsentResponse(String version, String text, String textHash) {
    }

    @GetMapping("/api/public/legal/recording-consent")
    public PublicConsentResponse current() {
        return new PublicConsentResponse(document.version(), document.text(), document.textHash());
    }
}
