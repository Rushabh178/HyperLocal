package com.HyperLocalSystem.app.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * Every gateway callback we processed, keyed by the gateway's event id. Gateways deliver the same
 * event more than once; the primary key makes the second delivery a no-op.
 */
@Entity
@Immutable
@Table(name = "payment_webhook_events")
@Getter
@NoArgsConstructor
public class PaymentWebhookEvent {

    @Id
    @Column(name = "event_id", length = 150)
    private String eventId;

    @Column(name = "gateway_reference", length = 100)
    private String gatewayReference;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    public PaymentWebhookEvent(String eventId, String gatewayReference, String eventType) {
        this.eventId = eventId;
        this.gatewayReference = gatewayReference;
        this.eventType = eventType;
    }
}
