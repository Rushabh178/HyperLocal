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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An order offered to one AVAILABLE rider. Only one OFFERED row per order at a time; accepting is
 * a conditional UPDATE (OFFERED and unexpired), so a late or duplicate accept can never win.
 */
@Entity
@Table(name = "task_offers", indexes = {
        @Index(name = "idx_task_offers_agent_status", columnList = "agent_id, status"),
        @Index(name = "idx_task_offers_order", columnList = "order_id"),
        @Index(name = "idx_task_offers_status_expires", columnList = "status, expires_at")
})
@Getter
@Setter
@NoArgsConstructor
public class TaskOffer {

    public enum Status { OFFERED, ACCEPTED, REJECTED, EXPIRED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "offer_id")
    private UUID id;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OFFERED;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "distance_meters", nullable = false)
    private int distanceMeters;

    @Column(name = "estimated_earnings", nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedEarnings;

    @Column(name = "reject_reason", length = 100)
    private String rejectReason;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
