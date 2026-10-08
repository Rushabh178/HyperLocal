package com.HyperLocalSystem.app.common.model;

public enum StopState {
    PENDING,
    EN_ROUTE,
    ARRIVED,
    COMPLETED,
    /** Removed from the route, e.g. Kirana stop stripped after every item was marked deficient. */
    SKIPPED
}
