package com.HyperLocalSystem.app.agent.entity;

import java.util.EnumSet;
import java.util.Set;

import static com.HyperLocalSystem.app.agent.entity.DocumentType.AADHAAR;
import static com.HyperLocalSystem.app.agent.entity.DocumentType.DRIVING_LICENSE;
import static com.HyperLocalSystem.app.agent.entity.DocumentType.PAN;
import static com.HyperLocalSystem.app.agent.entity.DocumentType.PROFILE_PHOTO;
import static com.HyperLocalSystem.app.agent.entity.DocumentType.VEHICLE_RC;

public enum VehicleType {
    MOTORCYCLE,
    SCOOTER,
    EV_SCOOTER,
    BICYCLE;

    /** KYC documents that must be uploaded before the application goes to review. */
    public Set<DocumentType> requiredDocuments() {
        return this == BICYCLE
                ? EnumSet.of(AADHAAR, PAN, PROFILE_PHOTO)
                : EnumSet.of(AADHAAR, PAN, PROFILE_PHOTO, DRIVING_LICENSE, VEHICLE_RC);
    }
}
