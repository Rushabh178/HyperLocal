package com.HyperLocalSystem.app.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public enum PaymentStatus {
        /** Waiting for the customer to approve the UPI mandate. */
        HOLD_PENDING,
        /** Funds blocked; nothing captured yet. */
        HELD,
        /** Merchant paid (Kirana QR scan) / order completed. */
        CAPTURED,
        /** Hold dropped, e.g. order cancelled. */
        RELEASED,
        PARTIALLY_REFUNDED,
        FAILED
    }

    public record PaymentHoldResponse(
            UUID paymentId,
            UUID orderId,
            BigDecimal amount,
            PaymentStatus status,
            /** upi://pay?... deep link for the UPI app. */
            String upiIntentUrl,
            Instant expiresAt) {
    }

    public enum WalletTransactionType { CREDIT, DEBIT }

    public record WalletResponse(BigDecimal balance, String currency) {
    }

    public record WalletTransaction(
            UUID id,
            WalletTransactionType type,
            BigDecimal amount,
            String reason,
            UUID orderId,
            Instant createdAt) {
    }
}
