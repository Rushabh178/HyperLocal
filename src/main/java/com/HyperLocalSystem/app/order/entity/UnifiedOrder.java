package com.HyperLocalSystem.app.order.entity;

import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.payment.entity.PaymentMethod;
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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps the existing {@code unified_orders} table. A combined cart produces one order with both
 * restaurant_id and kirana_id set; a solo order has one of them NULL.
 * <p>
 * Status fields only change through {@link #moveTo}, {@link #moveFoodTo} and {@link #moveGroceryTo}
 * (the order state machine). @Version turns concurrent updates into a conflict instead of a lost write.
 * Columns added to the original table are nullable or have a DB default so ddl-auto can add them
 * to a table that already has rows.
 */
@Entity
@Table(name = "unified_orders",
        uniqueConstraints = @UniqueConstraint(name = "uq_orders_idempotency", columnNames = {"customer_id", "idempotency_key"}),
        indexes = {
                @Index(name = "idx_orders_customer_created", columnList = "customer_id, created_at"),
                @Index(name = "idx_orders_status", columnList = "status"),
                @Index(name = "idx_orders_agent_status", columnList = "delivery_agent_id, status"),
                @Index(name = "idx_orders_restaurant", columnList = "restaurant_id, food_status"),
                @Index(name = "idx_orders_kirana", columnList = "kirana_id, grocery_status"),
                @Index(name = "idx_orders_parent_batch", columnList = "parent_batch_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class UnifiedOrder {

    // ---- existing columns ----

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

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "is_batched")
    private boolean batched;

    /** Primary order whose route this order was injected into. */
    @Column(name = "parent_batch_id")
    private UUID parentBatchId;

    /** What the customer pays (the bill total). */
    @Column(name = "financial_total", precision = 10, scale = 2)
    private BigDecimal financialTotal;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    // ---- added ----

    @Column(name = "delivery_address_id")
    private UUID deliveryAddressId;

    /** Snapshot of the address pin, so dispatch and batching never join the address table. */
    @Column(name = "drop_location", columnDefinition = "geometry(Point,4326)")
    private Point dropLocation;

    @Column(name = "drop_h3", length = 15)
    private String dropH3;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "food_status", length = 20)
    private PartStatus foodStatus;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "grocery_status", length = 20)
    private PartStatus groceryStatus;

    @Column(name = "food_subtotal", precision = 10, scale = 2)
    private BigDecimal foodSubtotal;

    @Column(name = "grocery_subtotal", precision = 10, scale = 2)
    private BigDecimal grocerySubtotal;

    @Column(name = "delivery_fee", precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    /** Road distance of the farthest pickup to the drop at checkout: what the delivery fee was based on. */
    @Column(name = "delivery_distance_m")
    private Integer deliveryDistanceMeters;

    @Column(name = "convenience_fee", precision = 10, scale = 2)
    private BigDecimal convenienceFee;

    @Column(precision = 10, scale = 2)
    private BigDecimal taxes;

    @Column(precision = 10, scale = 2)
    private BigDecimal discount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 10)
    private PaymentMethod paymentMethod;

    /** 4-digit code the customer reads out to the rider at the door. */
    @Column(name = "delivery_otp", length = 4)
    private String deliveryOtp;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    /** When the food should be ready (restaurant's prep time, pushed back by delays). */
    @Column(name = "prep_ready_at")
    private Instant prepReadyAt;

    /** The customer's note for the stores, from checkout. */
    @Column(name = "customer_note", length = 250)
    private String customerNote;

    @Column(name = "cancel_reason", length = 250)
    private String cancelReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(columnDefinition = "bigint default 0 not null")
    private long version;

    /** DB default so the column can be added to a table that already has rows. */
    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "timestamp(6) with time zone default now()")
    private Instant updatedAt;

    // ----------------------------------------------------------------- state machine

    public void moveTo(OrderStatus next) {
        if (!status.canMoveTo(next)) {
            throw illegal("Order", status, next);
        }
        status = next;
        if (next == OrderStatus.COMPLETED) {
            completedAt = Instant.now();
        } else if (next == OrderStatus.CANCELLED) {
            cancelledAt = Instant.now();
        }
    }

    public void moveFoodTo(PartStatus next) {
        if (foodStatus == null || !foodStatus.canMoveTo(next)) {
            throw illegal("Food", foodStatus, next);
        }
        foodStatus = next;
    }

    public void moveGroceryTo(PartStatus next) {
        if (groceryStatus == null || !groceryStatus.canMoveTo(next)) {
            throw illegal("Grocery part", groceryStatus, next);
        }
        groceryStatus = next;
    }

    /** Only at checkout: an order has a part only if the cart had items of that kind. */
    public void initParts(boolean hasFood, boolean hasGroceries) {
        foodStatus = hasFood ? PartStatus.AWAITING_PAYMENT : null;
        groceryStatus = hasGroceries ? PartStatus.AWAITING_PAYMENT : null;
    }

    public boolean hasFood() {
        return foodStatus != null;
    }

    public boolean hasGroceries() {
        return groceryStatus != null;
    }

    /** A Kirana has the groceries (packing or packed): its pickup belongs on the route. */
    public boolean isGroceryClaimed() {
        return kiranaId != null && (groceryStatus == PartStatus.CLAIMED || groceryStatus == PartStatus.READY);
    }

    /**
     * The goods are settled enough to send a rider: groceries claimed by a Kirana, or food with no
     * grocery part still being looked for. A mixed order waits until its groceries are claimed or
     * given up (UNFULFILLED), so the rider never leaves the restaurant before the Kirana stop exists.
     * Must match {@code UnifiedOrderRepository.AWAITING_RIDER}.
     */
    public boolean readyForRider() {
        return isGroceryClaimed() || hasFood() && (!hasGroceries() || groceryStatus == PartStatus.UNFULFILLED);
    }

    private static ApiException illegal(String what, Object from, Object to) {
        return ApiException.conflict("ILLEGAL_STATE_TRANSITION", what + " can't go from " + from + " to " + to);
    }
}
