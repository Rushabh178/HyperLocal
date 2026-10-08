package com.HyperLocalSystem.app.payment.service;

import com.HyperLocalSystem.app.common.exception.NotImplementedException;
import com.HyperLocalSystem.app.payment.dto.PaymentDtos.PaymentHoldResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * UPI "hold then capture" flow. TODO: integrate a gateway that supports UPI one-time mandates
 * (Razorpay / Cashfree / Juspay) and add a payments table.
 */
@Service
public class PaymentService {

    public PaymentHoldResponse createHold(UUID customerId, UUID orderId) {
        throw new NotImplementedException("UPI payment hold");
    }

    public PaymentHoldResponse status(UUID customerId, UUID orderId) {
        throw new NotImplementedException("Payment status");
    }

    /**
     * Gateway callback. TODO: verify the HMAC signature header BEFORE trusting the body; on
     * "hold confirmed" call DispatchOrchestrator.onPaymentConfirmed(orderId).
     */
    public void handleWebhook(String signature, String rawBody) {
        throw new NotImplementedException("Payment webhook");
    }

    /** Called when the rider scans the Kirana's in-store QR: pays the shop its share. */
    public void captureForMerchant(UUID orderId, UUID merchantId) {
        throw new NotImplementedException("Merchant payment capture");
    }

    /** Point-of-collection discrepancy: refund the missing items to the customer's wallet. */
    public void partialRefundToWallet(UUID orderId, BigDecimal amount, String reason) {
        throw new NotImplementedException("Partial refund");
    }

    public void releaseHold(UUID orderId) {
        throw new NotImplementedException("Release payment hold");
    }
}
