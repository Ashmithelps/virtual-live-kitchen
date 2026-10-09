package com.virtualkitchen.auth.user;

public enum UserStatus {
    ACTIVE,
    /** Restaurant owners start here and are activated by an admin. */
    PENDING_APPROVAL,
    /** Cannot log in or refresh (e.g. a chef removed by their owner). */
    DISABLED
}
