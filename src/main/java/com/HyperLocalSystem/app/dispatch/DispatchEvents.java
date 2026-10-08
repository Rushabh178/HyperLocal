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

    public enum WithdrawReason { EXPIRED, TAKEN, CANCELLED }

    /** The rider's open offer is gone; the app removes the offer card. */
    public record OfferWithdrawnEvent(UUID offerId, UUID agentId, UUID orderId, WithdrawReason reason) {
    }

    /** A Kirana broadcast round went out to its target shops. */
    public record BroadcastOpenedEvent(UUID broadcastId) {
    }

    public enum BroadcastCloseReason { CLAIMED, EXPIRED, CANCELLED }

    /** An open round ended; every target except the winner ({@code claimedBy}) loses it. */
    public record BroadcastClosedEvent(UUID broadcastId, UUID claimedBy, BroadcastCloseReason reason) {
    }

    public enum StopChange { ADDED, ARRIVED, COMPLETED, SKIPPED }

    /** One route stop changed (drives RIDER_ARRIVING / ORDER_PICKED_UP and the customer's STOPS_UPDATED). */
    public record StopChangedEvent(UUID stopId, UUID orderId, StopChange change) {
    }

    /** The rider's route changed without them doing anything (late Kirana stop, cancelled order). */
    public record RouteChangedEvent(UUID agentId) {
    }
}
