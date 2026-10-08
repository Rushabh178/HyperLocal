package com.HyperLocalSystem.app.payment.entity;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * What the platform owes a store for one order, released at pickup (Kirana QR scan / restaurant
 * hand-over). Append-only and unique per (merchant, order), so a repeated scan can't pay twice.
 */
@Entity
@Immutable
@Table(name = "merchant_settlements",
        uniqueConstraints = @UniqueConstraint(name = "uq_merchant_settlements", columnNames = {"merchant_id", "order_id"}),
        indexes = @Index(name = "idx_merchant_settlements_merchant", columnList = "merchant_id, created_at"))
@Getter
@NoArgsConstructor
public class MerchantSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "settlement_id")
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public MerchantSettlement(UUID merchantId, UUID orderId, BigDecimal amount) {
        this.merchantId = merchantId;
        this.orderId = orderId;
        this.amount = amount;
    }
}
