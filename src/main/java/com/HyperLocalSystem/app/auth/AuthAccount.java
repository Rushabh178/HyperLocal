package com.HyperLocalSystem.app.auth;

import com.HyperLocalSystem.app.common.model.AccountStatus;

import java.time.Instant;
import java.util.UUID;

/** What the shared sign-in flow needs from a customer, delivery agent or merchant account. */
public interface AuthAccount {

    UUID getId();

    String getPhone();

    String getPasswordHash();

    void setPasswordHash(String passwordHash);

    boolean isPhoneVerified();

    void setPhoneVerified(boolean phoneVerified);

    AccountStatus getAccountStatus();

    void setLastLoginAt(Instant lastLoginAt);
}
