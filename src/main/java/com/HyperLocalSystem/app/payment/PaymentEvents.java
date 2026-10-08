package com.HyperLocalSystem.app.payment;

import java.util.UUID;

/** Payment outcomes the customer app waits for after checkout (PAYMENT_CONFIRMED / PAYMENT_FAILED). */
public final class PaymentEvents {

    private PaymentEvents() {
    }

    /** The money is secured (UPI hold approved or wallet paid). */
    public record PaymentConfirmedEvent(UUID paymentId, UUID orderId) {
    }

    /** The UPI hold was declined; the order stays PENDING and can be retried. */
    public record PaymentFailedEvent(UUID paymentId, UUID orderId) {
    }
}
