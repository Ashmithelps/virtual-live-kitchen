package com.virtualkitchen.auth.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String name, String phone, String password) {

    @Override
    public String toString() {
        return "AdminProperties[name=" + name + ", phone=" + phone + ", password=***]";
    }
}
