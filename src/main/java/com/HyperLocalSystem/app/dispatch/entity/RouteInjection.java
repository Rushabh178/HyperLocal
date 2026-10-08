package com.HyperLocalSystem.app.dispatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** A solo grocery order the batching engine added to a rider's live route (the "+1 stop" flash). */
@Entity
@Table(name = "route_injections", indexes = {
        @Index(name = "idx_route_injections_agent", columnList = "agent_id"),
        @Index(name = "idx_route_injections_route", columnList = "route_id")
})
@Getter
@Setter
@NoArgsConstructor
public class RouteInjection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "injection_id")
    private UUID id;

    @Column(name = "route_id", nullable = false)
    private UUID routeId;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "injected_order_id", nullable = false)
    private UUID injectedOrderId;

    @Column(name = "primary_order_id", nullable = false)
    private UUID primaryOrderId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal bonus;

    @Column(name = "added_delay_seconds", nullable = false)
    private long addedDelaySeconds;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
