package com.HyperLocalSystem.app.agent.entity;

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

import java.time.Instant;
import java.util.UUID;

/**
 * Maps the existing {@code delivery_agents} table (agent_id, name, current_location, h3_index,
 * status, updated_at) and adds the sign-in, vehicle and KYC columns. Required-ness of the added
 * columns is enforced by the sign-up DTO rather than NOT NULL, so they can be added to a table
 * that already has rows.
 */
@Entity
@Table(name = "delivery_agents", indexes = {
        @Index(name = "idx_delivery_agents_h3_status", columnList = "h3_index, status")
})
@Getter
@Setter
@NoArgsConstructor
public class DeliveryAgent implements AuthAccount {

    // ---- existing columns ----

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "agent_id")
    private UUID id;

    @Column(name = "name", length = 100)
    private String name;

    /** Live GPS coordinate. */
    @Column(name = "current_location", columnDefinition = "geometry(Point,4326)")
    private Point currentLocation;

    /** Current Uber H3 hex cell. */
    @Column(name = "h3_index", length = 15)
    private String h3Index;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private DutyStatus dutyStatus = DutyStatus.OFFLINE;

    /** Last time the live location (or any field) changed. */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    // ---- added: sign-in ----

    @Column(unique = true, length = 15)
    private String phone;

    @Column(unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "phone_verified")
    private boolean phoneVerified;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", length = 20)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    // ---- added: onboarding ----

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", length = 20)
    private VehicleType vehicleType;

    @Column(name = "vehicle_number", length = 20)
    private String vehicleNumber;

    @Column(length = 60)
    private String city;

    /** Serial of the dual-compartment (70% hot / 30% cold) thermal box issued to the partner. */
    @Column(name = "thermal_box_id", length = 40)
    private String thermalBoxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING_DOCUMENTS;

    @Column(name = "rejection_reason", length = 250)
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
