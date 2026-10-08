package com.HyperLocalSystem.app.dispatch.entity;

public enum BroadcastStatus {
    /** Waiting for {@code starts_at} (e.g. held back while the kitchen cooks). */
    SCHEDULED,
    /** Pinged to its target Kiranas; the first "Items in Hand" tap wins. */
    OPEN,
    CLAIMED,
    /** Nobody claimed it before {@code expires_at}; the next round takes over. */
    EXPIRED,
    /** The order was cancelled. */
    CANCELLED
}
