package com.HyperLocalSystem.app.payment.gateway;

import com.HyperLocalSystem.app.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * UPI "hold then capture" gateway. The dev default is {@link FakePaymentGateway} (auto-succeeds);
 * {@link RazorpayPaymentGateway} is selected with the "razorpay" profile.
 */
public interface PaymentGateway {

    /** Stored on the payment row, e.g. FAKE or RAZORPAY. */
    String name();

    /**
     * @param status HELD when the gateway confirms synchronously, FAILED when it declines at once,
     *               else HOLD_PENDING until the webhook
     */
    record HoldResult(String reference, PaymentStatus status, String upiIntentUrl, Instant expiresAt) {
    }

    /** UPI intent flow only (no collect / VPA): the app opens {@code upiIntentUrl} while the hold is pending. */
    HoldResult createHold(UUID paymentId, BigDecimal amount);

    /**
     * Takes (part of) the held amount. {@code reference} is the payment's gateway payment id when the
     * gateway gave one, else the hold reference.
     */
    void capture(String reference, BigDecimal amount);

    /** Drops the hold without taking money. */
    void release(String reference);

    /** Returns captured money (or part of it) to the customer's bank account. */
    void refund(String reference, BigDecimal amount);

    /** Checks the callback signature over the exact raw body. Must be constant-time. */
    boolean verifySignature(String rawBody, String signature);

    enum WebhookType { HOLD_CONFIRMED, HOLD_FAILED, IGNORED }

    /**
     * {@code eventId} is the gateway's id for the event (the same on every redelivery) and is what
     * makes callbacks idempotent. {@code gatewayPaymentId} is set when the gateway reveals its payment id.
     */
    record WebhookEvent(String eventId, String reference, WebhookType type, String gatewayPaymentId) {
    }

    WebhookEvent parseWebhook(String rawBody);
}
