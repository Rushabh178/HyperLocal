package com.HyperLocalSystem.app.dispatch.entity;

import com.HyperLocalSystem.app.common.model.StopState;
import com.HyperLocalSystem.app.common.model.StopType;
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
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

/** One stop of a rider's route: a pickup (Kirana / restaurant) or a drop (customer) for one order. */
@Entity
@Table(name = "route_stops", indexes = {
        @Index(name = "idx_route_stops_route_sequence", columnList = "route_id, sequence"),
        @Index(name = "idx_route_stops_order", columnList = "order_id"),
        @Index(name = "idx_route_stops_agent_state", columnList = "agent_id, state")
})
@Getter
@Setter
@NoArgsConstructor
public class RouteStop {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "stop_id")
    private UUID id;

    @Column(name = "route_id", nullable = false)
    private UUID routeId;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_type", nullable = false, length = 20)
    private StopType type;

    /** The Kirana / restaurant for pickups; null for the customer drop. */
    @Column(name = "merchant_id")
    private UUID merchantId;

    /** Position in the locked route; the rider always works the lowest unfinished one. */
    @Column(nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StopState state = StopState.PENDING;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 300)
    private String address;

    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column
    private Instant eta;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isDone() {
        return state == StopState.COMPLETED || state == StopState.SKIPPED;
    }

    public boolean isPickup() {
        return type != StopType.CUSTOMER;
    }
}
