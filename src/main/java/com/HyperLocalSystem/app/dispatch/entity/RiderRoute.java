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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A rider's locked, linear multi-stop route. At most one ACTIVE route per rider (partial unique
 * index in db/indexes.sql). Every change to its stops bumps {@code version}, so two concurrent
 * edits (e.g. a batching injection and a stop completion) can't silently overwrite each other.
 */
@Entity
@Table(name = "rider_routes", indexes = @Index(name = "idx_rider_routes_agent_status", columnList = "agent_id, status"))
@Getter
@Setter
@NoArgsConstructor
public class RiderRoute {

    public enum Status { ACTIVE, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "route_id")
    private UUID id;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RiderRoute(UUID agentId) {
        this.agentId = agentId;
    }
}
