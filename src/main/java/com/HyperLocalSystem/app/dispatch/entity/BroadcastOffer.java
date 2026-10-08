package com.HyperLocalSystem.app.dispatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One round of the Kirana broadcast for an order's groceries. All timing lives in the row
 * (starts_at / expires_at) and a scheduled poller advances rounds, so nothing depends on an
 * in-memory timer and any instance can pick up the work.
 */
@Entity
@Table(name = "broadcast_offers", indexes = {
        @Index(name = "idx_broadcast_offers_order", columnList = "order_id"),
        @Index(name = "idx_broadcast_offers_status_expires", columnList = "status, expires_at"),
        @Index(name = "idx_broadcast_offers_status_starts", columnList = "status, starts_at")
})
@Getter
@Setter
@NoArgsConstructor
public class BroadcastOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "offer_id")
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    /** 1 = closest ring; each later round widens the H3 disk. */
    @Column(nullable = false)
    private int round;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BroadcastStatus status;

    /** Where distances are measured from: the restaurant for combined orders, else the customer. */
    @Column(name = "origin_lat", nullable = false)
    private double originLat;

    @Column(name = "origin_lng", nullable = false)
    private double originLng;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    /** Set when the round opens. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "claimed_by")
    private UUID claimedBy;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
