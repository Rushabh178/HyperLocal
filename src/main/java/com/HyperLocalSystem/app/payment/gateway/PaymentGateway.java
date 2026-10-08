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
     * @param status HELD when the gateway confirms synchronously (fake), else HOLD_PENDING until the webhook
     */
    record HoldResult(String reference, PaymentStatus status, String upiIntentUrl, Instant expiresAt) {
    }

    HoldResult createHold(UUID paymentId, BigDecimal amount, String upiVpa);

    /** Takes (part of) the held amount. */
    void capture(String reference, BigDecimal amount);

    /** Drops the hold without taking money. */
    void release(String reference);

    /** Returns captured money to the customer's bank account. */
    void refund(String reference, BigDecimal amount);

    /** Checks the callback signature over the exact raw body. Must be constant-time. */
    boolean verifySignature(String rawBody, String signature);

    enum WebhookType { HOLD_CONFIRMED, HOLD_FAILED, IGNORED }

    /** {@code eventId} is unique per delivery attempt group and is what makes callbacks idempotent. */
    record WebhookEvent(String eventId, String reference, WebhookType type) {
    }

    WebhookEvent parseWebhook(String rawBody);
}
