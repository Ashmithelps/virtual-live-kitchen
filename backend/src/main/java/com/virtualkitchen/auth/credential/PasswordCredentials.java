package com.virtualkitchen.auth.credential;

public record PasswordCredentials(String phone, String password) implements LoginCredentials {

    @Override
    public String toString() {
        return "PasswordCredentials[phone=" + phone + ", password=***]";
    }
}
