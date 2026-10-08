package com.HyperLocalSystem.app.payment.dto;

import com.HyperLocalSystem.app.payment.entity.PaymentStatus;
import com.HyperLocalSystem.app.payment.entity.WalletTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() {
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
