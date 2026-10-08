package com.HyperLocalSystem.app.payment.entity;

import com.HyperLocalSystem.app.common.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The one payment of an order (retries reuse the row). Status only changes through
 * {@link #moveTo}, which enforces {@link PaymentStatus#canMoveTo}; @Version makes concurrent
 * updates (webhook vs. cancel) fail instead of overwriting each other.
 */
@Entity
@Table(name = "payments", indexes = @Index(name = "idx_payments_customer", columnList = "customer_id"))
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payment_id")
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentMethod method;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.HOLD_PENDING;

    /** FAKE, RAZORPAY, WALLET ... */
    @Column(nullable = false, length = 20)
    private String gateway;

    /** The gateway's id for the hold; webhooks are matched on it. */
    @Column(name = "gateway_reference", unique = true, length = 100)
    private String gatewayReference;

    /** Some gateways (Razorpay) capture/refund by a payment id they only reveal in the webhook. */
    @Column(name = "gateway_payment_id", length = 100)
    private String gatewayPaymentId;

    @Column(name = "upi_intent_url", length = 500)
    private String upiIntentUrl;

    @Column(name = "captured_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal capturedAmount = BigDecimal.ZERO;

    /** Sent back to the customer's wallet so far (missing items, cancellation). */
    @Column(name = "refunded_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** The payment state machine: every status change goes through here. */
    public void moveTo(PaymentStatus next) {
        if (!status.canMoveTo(next)) {
            throw ApiException.conflict("ILLEGAL_STATE_TRANSITION", "Payment can't go from " + status + " to " + next);
        }
        status = next;
    }

    /** Funds are secured: either blocked (UPI hold) or already taken (wallet). */
    public boolean isSecured() {
        return status == PaymentStatus.HELD || status == PaymentStatus.CAPTURED;
    }
}
