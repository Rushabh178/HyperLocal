package com.HyperLocalSystem.app.auth.entity;

public enum OtpPurpose {
    /** Confirms phone ownership right after sign-up. */
    SIGNUP_VERIFY,
    /** Password-less login. */
    LOGIN,
    PASSWORD_RESET
}
