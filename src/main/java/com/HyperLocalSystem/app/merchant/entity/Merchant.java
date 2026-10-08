package com.HyperLocalSystem.app.merchant.entity;

import com.HyperLocalSystem.app.auth.AuthAccount;
import com.HyperLocalSystem.app.common.model.AccountStatus;
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
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * A Kirana shop or a restaurant. Signs in through the merchant app (ROLE_MERCHANT).
 * The GiST index on {@code location} lives in db/indexes.sql because JPA can't declare it.
 */
@Entity
@Table(name = "merchants", indexes = {
        @Index(name = "idx_merchants_h3", columnList = "h3_index"),
        @Index(name = "idx_merchants_discovery", columnList = "merchant_type, approval_status, is_open")
})
@Getter
@Setter
@NoArgsConstructor
public class Merchant implements AuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "merchant_id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "merchant_type", nullable = false, length = 12)
    private MerchantType type;

    @Column(name = "store_name", nullable = false, length = 120)
    private String storeName;

    @Column(name = "owner_name", nullable = false, length = 100)
    private String ownerName;

    /** E.164, e.g. +919876543210. */
    @Column(nullable = false, unique = true, length = 15)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    // ---- store ----

    @Column(nullable = false, length = 300)
    private String address;

    @Column(nullable = false, length = 6)
    private String pincode;

    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(name = "h3_index", nullable = false, length = 15)
    private String h3Index;

    @Column(name = "is_open", nullable = false)
    private boolean open;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Column(name = "rejection_reason", length = 250)
    private String rejectionReason;

    /** Mandatory for restaurants. */
    @Column(name = "fssai_number", length = 14)
    private String fssaiNumber;

    @Column(length = 15)
    private String gstin;

    /** Where settlements are paid. */
    @Column(name = "upi_vpa", length = 100)
    private String upiVpa;

    /** Restaurants only, comma-separated, e.g. "North Indian,Chinese". */
    @Column(length = 200)
    private String cuisines;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // ---- ratings (denormalised so discovery never aggregates order_ratings) ----

    @Column(name = "rating_sum", nullable = false)
    private int ratingSum;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Average rating to one decimal, or null before the first rating. */
    public BigDecimal rating() {
        return ratingCount == 0 ? null
                : BigDecimal.valueOf(ratingSum).divide(BigDecimal.valueOf(ratingCount), 1, RoundingMode.HALF_UP);
    }
}
