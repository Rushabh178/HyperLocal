package com.HyperLocalSystem.app.dispatch;

import java.util.UUID;

/** Internal dispatch events (consumed after commit, asynchronously). */
public final class DispatchEvents {

    private DispatchEvents() {
    }

    /** The order needs a (new) rider now, e.g. the previous one rejected. The poller is the safety net. */
    public record RiderNeededEvent(UUID orderId) {
    }

    /** Push the new offer to the rider's app. */
    public record TaskOfferCreatedEvent(UUID offerId, UUID agentId) {
    }
}
