package com.HyperLocalSystem.app.payment.entity;

public enum PaymentMethod {
    /** UPI one-time mandate: hold at checkout, capture later. */
    UPI,
    /** Paid instantly from the in-app wallet balance. */
    WALLET
}
