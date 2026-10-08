package com.HyperLocalSystem.app.payment.service;

import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.common.exception.NotImplementedException;
import com.HyperLocalSystem.app.payment.dto.PaymentDtos.PaymentHoldResponse;
import com.HyperLocalSystem.app.payment.entity.Payment;
import com.HyperLocalSystem.app.payment.entity.PaymentMethod;
import com.HyperLocalSystem.app.payment.entity.PaymentStatus;
import com.HyperLocalSystem.app.payment.gateway.PaymentGateway;
import com.HyperLocalSystem.app.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * UPI "hold then capture" flow plus instant wallet payments. Every status change goes through
 * {@link Payment#moveTo}. Gateway calls happen inside the DB transaction for simplicity.
 * TODO(edge-case): move gateway calls to an outbox so a DB rollback can't orphan a real hold.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String WALLET_GATEWAY = "WALLET";

    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final WalletService walletService;

    /**
     * Called by checkout. UPI: creates the hold (the fake gateway approves it at once, a real one
     * confirms via webhook). WALLET: debits the wallet and captures immediately.
     */
    @Transactional
    public Payment start(UUID orderId, UUID customerId, BigDecimal amount, PaymentMethod method, String upiVpa) {
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setCustomerId(customerId);
        payment.setMethod(method);
        payment.setAmount(amount);
        if (method == PaymentMethod.WALLET) {
            payment.setGateway(WALLET_GATEWAY);
            payments.saveAndFlush(payment);
            walletService.debit(customerId, amount, "Order payment", orderId, "pay:order:" + orderId);
            payment.moveTo(PaymentStatus.HELD);
            payment.moveTo(PaymentStatus.CAPTURED);
            payment.setCapturedAmount(amount);
            return payment;
        }
        payment.setGateway(gateway.name());
        payments.saveAndFlush(payment);
        PaymentGateway.HoldResult hold = gateway.createHold(payment.getId(), amount, upiVpa);
        payment.setGatewayReference(hold.reference());
        payment.setUpiIntentUrl(hold.upiIntentUrl());
        payment.setExpiresAt(hold.expiresAt());
        if (hold.status() == PaymentStatus.HELD) {
            payment.moveTo(PaymentStatus.HELD);
        }
        return payment;
    }

    /** Retry / re-create the UPI hold for an order (checkout already creates the first one). */
    public PaymentHoldResponse createHold(UUID customerId, UUID orderId) {
        throw new NotImplementedException("UPI payment hold retry");
    }

    @Transactional(readOnly = true)
    public PaymentHoldResponse status(UUID customerId, UUID orderId) {
        return payments.findByOrderId(orderId)
                .filter(payment -> payment.getCustomerId().equals(customerId))
                .map(PaymentService::toResponse)
                .orElseThrow(() -> ApiException.notFound("Payment"));
    }

    /**
     * Gateway callback. TODO: verify the HMAC signature header BEFORE trusting the body; on
     * "hold confirmed" confirm the order.
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

    /**
     * Gives the customer their money back for a cancelled order: drops an uncaptured hold, or
     * refunds whatever was captured (minus earlier refunds) to the wallet.
     */
    @Transactional
    public void releaseOrRefund(UUID orderId, String reason) {
        payments.findByOrderId(orderId).ifPresent(payment -> {
            switch (payment.getStatus()) {
                case HOLD_PENDING, HELD -> {
                    if (payment.getGatewayReference() != null) {
                        gateway.release(payment.getGatewayReference());
                    }
                    payment.moveTo(PaymentStatus.RELEASED);
                }
                case CAPTURED, PARTIALLY_REFUNDED -> {
                    BigDecimal refundable = payment.getCapturedAmount().subtract(payment.getRefundedAmount());
                    walletService.credit(payment.getCustomerId(), refundable, reason, orderId, "refund:cancel:" + orderId);
                    payment.setRefundedAmount(payment.getRefundedAmount().add(refundable));
                    payment.moveTo(PaymentStatus.REFUNDED);
                }
                case RELEASED, REFUNDED, FAILED -> {
                }
            }
        });
    }

    public static PaymentHoldResponse toResponse(Payment payment) {
        return new PaymentHoldResponse(payment.getId(), payment.getOrderId(), payment.getAmount(), payment.getStatus(),
                payment.getUpiIntentUrl(), payment.getExpiresAt());
    }
}
