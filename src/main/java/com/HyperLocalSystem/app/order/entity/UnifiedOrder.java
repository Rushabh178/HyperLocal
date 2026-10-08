package com.HyperLocalSystem.app.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps the existing {@code unified_orders} table. A combined cart produces one order with both
 * restaurant_id and kirana_id set; a solo order has one of them NULL.
 */
@Entity
@Table(name = "unified_orders")
@Getter
@Setter
@NoArgsConstructor
public class UnifiedOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "order_id")
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    /** NULL for a solo grocery order. */
    @Column(name = "restaurant_id")
    private UUID restaurantId;

    /** NULL for a solo food order; set when a Kirana claims the broadcast. */
    @Column(name = "kirana_id")
    private UUID kiranaId;

    @Column(name = "delivery_agent_id")
    private UUID deliveryAgentId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "is_batched")
    private boolean batched;

    /** Primary order whose route this order was injected into. */
    @Column(name = "parent_batch_id")
    private UUID parentBatchId;

    @Column(name = "financial_total", precision = 10, scale = 2)
    private BigDecimal financialTotal;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    // ---- added ----

    @Column(name = "delivery_address_id")
    private UUID deliveryAddressId;
}
