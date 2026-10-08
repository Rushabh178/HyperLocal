package com.HyperLocalSystem.app.security;

/** Carried in the JWT "role" claim; Spring Security sees it as ROLE_{name}. */
public enum Role {
    CUSTOMER,
    DELIVERY_AGENT,
    MERCHANT,
    ADMIN
}
