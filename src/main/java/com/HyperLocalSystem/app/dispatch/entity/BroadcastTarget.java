package com.HyperLocalSystem.app.dispatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** A Kirana pinged in one broadcast round. Only targeted Kiranas can claim that round. */
@Entity
@Table(name = "broadcast_targets",
        uniqueConstraints = @UniqueConstraint(name = "uq_broadcast_targets", columnNames = {"offer_id", "merchant_id"}),
        indexes = @Index(name = "idx_broadcast_targets_merchant", columnList = "merchant_id"))
@Getter
@Setter
@NoArgsConstructor
public class BroadcastTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "target_id")
    private UUID id;

    @Column(name = "offer_id", nullable = false)
    private UUID offerId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "distance_meters", nullable = false)
    private int distanceMeters;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public BroadcastTarget(UUID offerId, UUID merchantId, int distanceMeters) {
        this.offerId = offerId;
        this.merchantId = merchantId;
        this.distanceMeters = distanceMeters;
    }
}
