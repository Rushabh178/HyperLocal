package com.HyperLocalSystem.app.common.model;

public enum AccountStatus {
    ACTIVE,
    BLOCKED,
    /** Soft-deleted by the user; the phone number can register again later. */
    DELETED
}
