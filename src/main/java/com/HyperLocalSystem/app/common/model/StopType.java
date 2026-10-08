package com.HyperLocalSystem.app.common.model;

/** A stop on a rider's linear multi-stop route: Kirana → Restaurant → Customer. */
public enum StopType {
    KIRANA,
    RESTAURANT,
    /** Fallback when no Kirana claims the broadcast (blueprint: "primary commercial dark store hub"). */
    DARK_STORE,
    CUSTOMER
}
