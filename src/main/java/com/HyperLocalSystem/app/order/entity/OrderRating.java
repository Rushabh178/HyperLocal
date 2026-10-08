package com.HyperLocalSystem.app.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** One rating per order (the order id is the primary key). */
@Entity
@Table(name = "order_ratings", indexes = @Index(name = "idx_order_ratings_customer", columnList = "customer_id"))
@Getter
@Setter
@NoArgsConstructor
public class OrderRating {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "delivery_rating")
    private Integer deliveryRating;

    @Column(name = "restaurant_rating")
    private Integer restaurantRating;

    @Column(name = "kirana_rating")
    private Integer kiranaRating;

    @Column(length = 500)
    private String comment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
