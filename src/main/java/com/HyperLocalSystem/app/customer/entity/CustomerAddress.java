package com.HyperLocalSystem.app.customer.entity;

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

import java.time.Instant;
import java.util.UUID;

/**
 * Saved delivery address. {@code location} is the map pin the customer dropped and is what
 * routing uses; the text fields are only shown to the rider.
 */
@Entity
@Table(name = "customer_addresses", indexes = {
        @Index(name = "idx_customer_addresses_customer", columnList = "customer_id"),
        @Index(name = "idx_customer_addresses_h3", columnList = "h3_index")
})
@Getter
@Setter
@NoArgsConstructor
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "address_id")
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AddressLabel label;

    /** Free text when label is OTHER, e.g. "Mom's place". */
    @Column(name = "custom_label", length = 30)
    private String customLabel;

    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @Column(name = "recipient_phone", length = 15)
    private String recipientPhone;

    @Column(name = "house_number", nullable = false, length = 50)
    private String houseNumber;

    @Column(name = "building_name", length = 100)
    private String buildingName;

    @Column(length = 150)
    private String street;

    @Column(length = 150)
    private String landmark;

    @Column(length = 100)
    private String area;

    @Column(nullable = false, length = 60)
    private String city;

    @Column(nullable = false, length = 60)
    private String state;

    @Column(nullable = false, length = 6)
    private String pincode;

    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(name = "h3_index", length = 15)
    private String h3Index;

    @Column(name = "delivery_instructions", length = 250)
    private String deliveryInstructions;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    /** Soft delete: past orders keep pointing at the address they were delivered to. */
    @Column(nullable = false)
    private boolean deleted;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
