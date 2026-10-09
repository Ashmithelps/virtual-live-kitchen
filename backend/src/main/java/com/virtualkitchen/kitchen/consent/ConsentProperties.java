package com.virtualkitchen.kitchen.consent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.chef-consent")
public record ConsentProperties(@NotBlank @Pattern(regexp = "^v[0-9]+$") String currentVersion) {
}
