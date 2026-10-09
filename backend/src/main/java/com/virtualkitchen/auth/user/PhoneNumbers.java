package com.virtualkitchen.auth.user;

public final class PhoneNumbers {

    /** E.164: leading '+', country code, 10–15 digits total. Phones are stored exactly in this form. */
    public static final String E164_REGEX = "^\\+[1-9][0-9]{9,14}$";
    public static final String E164_MESSAGE = "must be in E.164 format, e.g. +919876543210";

    private PhoneNumbers() {
    }
}
