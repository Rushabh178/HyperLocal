package com.HyperLocalSystem.app.realtime;

/** The {@code type} of every real-time message (WebSocket envelope and push data payload). */
public enum EventType {

    // ---- customer, on /user/queue/tracking
    ORDER_STATUS,
    RIDER_LOCATION,
    STOPS_UPDATED,
    PAYMENT_CONFIRMED,
    PAYMENT_FAILED,
    /** The groceries of a mixed order couldn't be got; their share went back to the wallet. */
    GROCERIES_UNFULFILLED,

    // ---- rider, on /user/queue/route-updates
    OFFER,
    /** The offer is gone: expired, taken by batching or the order was cancelled. */
    OFFER_WITHDRAWN,
    /** A batching injection added stops (the "+1 stop" flash, with the bonus). */
    STOP_ADDED,
    /** The route changed for another reason (late Kirana stop, cancelled order). */
    ROUTE_UPDATED,

    // ---- merchant, on /user/queue/merchant-events
    NEW_ORDER,
    ORDER_CANCELLED,
    BROADCAST_OFFER,
    /** Claimed by another shop or expired. */
    BROADCAST_TAKEN,
    RIDER_ARRIVING,
    ORDER_PICKED_UP
}
