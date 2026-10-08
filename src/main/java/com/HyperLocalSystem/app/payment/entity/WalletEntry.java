package com.HyperLocalSystem.app.payment.entity;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only wallet ledger: rows are only ever inserted (no setters, @Immutable), and the balance
 * is SUM(credits) - SUM(debits). {@code reference} is unique so a retried refund can't be paid twice.
 */
@Entity
@Immutable
@Table(name = "wallet_entries", indexes = {
        @Index(name = "idx_wallet_entries_customer", columnList = "customer_id, created_at"),
        @Index(name = "idx_wallet_entries_order", columnList = "order_id")
})
@Getter
@NoArgsConstructor
public class WalletEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "entry_id")
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private WalletTransactionType type;

    /** Always positive; {@code type} gives the direction. */
    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 150)
    private String reason;

    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    /** Idempotency key of the business event, e.g. "refund:order:<id>". */
    @Column(nullable = false, unique = true, updatable = false, length = 120)
    private String reference;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public WalletEntry(UUID customerId, WalletTransactionType type, BigDecimal amount, String reason,
                       UUID orderId, String reference) {
        this.customerId = customerId;
        this.type = type;
        this.amount = amount;
        this.reason = reason;
        this.orderId = orderId;
        this.reference = reference;
    }
}
