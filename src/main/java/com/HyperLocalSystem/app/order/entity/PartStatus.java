package com.HyperLocalSystem.app.order.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Progress of one half of an order: the food part (restaurant kitchen) or the grocery part
 * (Kirana broadcast). The order's own status says where the rider is; these say where the goods are.
 */
public enum PartStatus {
    /** Order not paid yet. */
    AWAITING_PAYMENT,
    /** Food: kitchen ticket sent, waiting for the restaurant to accept. */
    NEW,
    /** Groceries: offered to nearby Kiranas, nobody has claimed it yet. */
    BROADCASTING,
    /** Food: restaurant accepted and is preparing. */
    ACCEPTED,
    /** Food: restaurant reported a kitchen delay. */
    DELAYED,
    /** Food ready at the counter / Kirana tapped "Items in Hand". */
    READY,
    PICKED_UP,
    /** Groceries: no Kirana claimed it after every broadcast round. */
    UNFULFILLED,
    CANCELLED;

    /** The only legal moves; {@link UnifiedOrder#moveFoodTo} / {@link UnifiedOrder#moveGroceryTo} reject the rest. */
    public boolean canMoveTo(PartStatus next) {
        Set<PartStatus> allowed = switch (this) {
            case AWAITING_PAYMENT -> EnumSet.of(NEW, BROADCASTING, CANCELLED);
            case NEW -> EnumSet.of(ACCEPTED, CANCELLED);
            case BROADCASTING -> EnumSet.of(READY, UNFULFILLED, CANCELLED);
            case ACCEPTED, DELAYED -> EnumSet.of(DELAYED, READY, PICKED_UP, CANCELLED);
            case READY -> EnumSet.of(PICKED_UP, UNFULFILLED, CANCELLED);
            case PICKED_UP, UNFULFILLED, CANCELLED -> EnumSet.noneOf(PartStatus.class);
        };
        return allowed.contains(next);
    }

    public boolean isTerminal() {
        return this == PICKED_UP || this == UNFULFILLED || this == CANCELLED;
    }
}
