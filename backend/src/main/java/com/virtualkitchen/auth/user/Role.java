package com.virtualkitchen.auth.user;

public enum Role {
    CUSTOMER,
    RESTAURANT_OWNER,
    CHEF,
    ADMIN;

    /** Spring Security authority name, e.g. {@code ROLE_ADMIN}; used by {@code hasRole('ADMIN')}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
