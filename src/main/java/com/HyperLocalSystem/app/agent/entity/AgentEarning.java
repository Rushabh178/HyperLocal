package com.HyperLocalSystem.app.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Append-only earnings ledger of a rider. One row per (rider, order, type), so a retried delivery
 * can't pay twice; totals are sums over a period.
 */
@Entity
@Immutable
@Table(name = "agent_earnings",
        uniqueConstraints = @UniqueConstraint(name = "uq_agent_earnings", columnNames = {"agent_id", "order_id", "earning_type"}),
        indexes = @Index(name = "idx_agent_earnings_agent_created", columnList = "agent_id, created_at"))
@Getter
@NoArgsConstructor
public class AgentEarning {

    public enum Type {
        /** Distance pay: app.rider.per-km-rate for each km of the order's route beyond app.rider.distance-pay-after-km. */
        DISTANCE,
        /** Single base payout per delivered order. */
        BASE,
        /** Extra pickups on the same order (Kirana + restaurant). */
        MULTI_STOP_BONUS,
        /** "+₹15" for accepting a batching injection. */
        BATCH_INCENTIVE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "earning_id")
    private UUID id;

    @Column(name = "agent_id", nullable = false, updatable = false)
    private UUID agentId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "earning_type", nullable = false, updatable = false, length = 20)
    private Type type;

    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AgentEarning(UUID agentId, UUID orderId, Type type, BigDecimal amount) {
        this.agentId = agentId;
        this.orderId = orderId;
        this.type = type;
        this.amount = amount;
    }
}
