package com.HyperLocalSystem.app.agent.entity;

/** KYC onboarding state of a delivery partner. Only APPROVED partners can go on duty. */
public enum VerificationStatus {
    PENDING_DOCUMENTS,
    UNDER_REVIEW,
    APPROVED,
    REJECTED
}
