package com.HyperLocalSystem.app.catalog.entity;

import com.HyperLocalSystem.app.common.model.TemperatureZone;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A dish on a restaurant menu (FOOD) or a product a Kirana lists (GROCERY). Grocery listings feed
 * the "virtual warehouse": customers see the products listed by Kiranas within 2 km, and whichever
 * nearby Kirana claims the broadcast fulfils the order.
 */
@Entity
@Table(name = "catalog_items", indexes = {
        @Index(name = "idx_catalog_items_merchant", columnList = "merchant_id"),
        @Index(name = "idx_catalog_items_type_available", columnList = "item_type, available"),
        @Index(name = "idx_catalog_items_category", columnList = "category")
})
@Getter
@Setter
@NoArgsConstructor
public class CatalogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "item_id")
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 10)
    private ItemType type;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Printed MRP (groceries); price may be lower. */
    @Column(precision = 10, scale = 2)
    private BigDecimal mrp;

    @Column(length = 60)
    private String brand;

    /** e.g. "500 g", "1 L". */
    @Column(name = "pack_size", length = 30)
    private String packSize;

    @Column(nullable = false, length = 60)
    private String category;

    private Boolean veg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TemperatureZone zone;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private boolean available;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
