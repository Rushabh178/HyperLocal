package com.HyperLocalSystem.app.payment.service;

import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.payment.dto.PaymentDtos.PaymentHoldResponse;
import com.HyperLocalSystem.app.payment.entity.MerchantSettlement;
import com.HyperLocalSystem.app.payment.entity.Payment;
import com.HyperLocalSystem.app.payment.entity.PaymentMethod;
import com.HyperLocalSystem.app.payment.entity.PaymentStatus;
import com.HyperLocalSystem.app.payment.gateway.PaymentGateway;
import com.HyperLocalSystem.app.payment.repository.MerchantSettlementRepository;
import com.HyperLocalSystem.app.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * UPI "hold then capture" flow plus instant wallet payments. Every status change goes through
 * {@link Payment#moveTo}. Gateway calls happen inside the DB transaction for simplicity.
 * TODO(edge-case): move gateway calls to an outbox so a DB rollback can't orphan a real hold.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String WALLET_GATEWAY = "WALLET";

    private final PaymentRepository payments;
    private final MerchantSettlementRepository settlements;
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

    /**
     * Retry after a failed UPI hold: a fresh hold on the same payment row. Returns the payment; the
     * caller confirms the order if it is already secured.
     */
    @Transactional
    public Payment retryHold(UUID customerId, UUID orderId) {
        Payment payment = payments.findByOrderId(orderId)
                .filter(p -> p.getCustomerId().equals(customerId))
                .orElseThrow(() -> ApiException.notFound("Payment"));
        if (payment.getMethod() != PaymentMethod.UPI || payment.getStatus() != PaymentStatus.FAILED) {
            throw ApiException.conflict("HOLD_NOT_RETRYABLE", "Only a failed UPI payment can be retried");
        }
        payment.moveTo(PaymentStatus.HOLD_PENDING);
        PaymentGateway.HoldResult hold = gateway.createHold(payment.getId(), payment.getAmount(), null);
        payment.setGatewayReference(hold.reference());
        payment.setGatewayPaymentId(null);
        payment.setUpiIntentUrl(hold.upiIntentUrl());
        payment.setExpiresAt(hold.expiresAt());
        if (hold.status() == PaymentStatus.HELD) {
            payment.moveTo(PaymentStatus.HELD);
        }
        return payment;
    }

    /**
     * Applies a verified gateway event to its payment. Returns the payment when this event secured
     * it (so the caller can confirm the order), else empty. Unknown references are ignored.
     */
    @Transactional
    public Optional<Payment> applyWebhook(PaymentGateway.WebhookEvent event) {
        Optional<Payment> found = payments.findByGatewayReference(event.reference());
        if (found.isEmpty()) {
            log.warn("Payment webhook {} for unknown reference {}", event.eventId(), event.reference());
            return Optional.empty();
        }
        Payment payment = found.get();
        if (event.gatewayPaymentId() != null) {
            payment.setGatewayPaymentId(event.gatewayPaymentId());
        }
        if (payment.getStatus() != PaymentStatus.HOLD_PENDING) {
            return Optional.empty();
        }
        switch (event.type()) {
            case HOLD_CONFIRMED -> {
                payment.moveTo(PaymentStatus.HELD);
                return Optional.of(payment);
            }
            case HOLD_FAILED -> payment.moveTo(PaymentStatus.FAILED);
            case IGNORED -> {
            }
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public PaymentHoldResponse status(UUID customerId, UUID orderId) {
        return payments.findByOrderId(orderId)
                .filter(payment -> payment.getCustomerId().equals(customerId))
                .map(PaymentService::toResponse)
                .orElseThrow(() -> ApiException.notFound("Payment"));
    }

    /**
     * Pickup at a store (Kirana QR scan / restaurant hand-over): takes the customer's money if it is
     * still only held, and records what the store is owed. Idempotent per (store, order).
     */
    @Transactional
    public void captureForMerchant(UUID orderId, UUID merchantId, BigDecimal amount) {
        Payment payment = load(orderId);
        ensureCaptured(payment);
        if (!settlements.existsByMerchantIdAndOrderId(merchantId, orderId)) {
            settlements.save(new MerchantSettlement(merchantId, orderId, amount));
        }
        // TODO(edge-case): platform commission and the actual bank payout to the store.
    }

    /** Delivery: make sure the money is taken (normally already done at the first pickup). */
    @Transactional
    public void ensureCaptured(UUID orderId) {
        ensureCaptured(load(orderId));
    }

    /**
     * Missing items / unavailable groceries: credit {@code amount} to the customer's wallet.
     * A still-held UPI payment is captured first, so a later cancellation can only ever refund what
     * is left. Idempotent on {@code reference}.
     */
    @Transactional
    public void partialRefundToWallet(UUID orderId, BigDecimal amount, String reason, String reference) {
        Payment payment = load(orderId);
        ensureCaptured(payment);
        BigDecimal refundable = payment.getCapturedAmount().subtract(payment.getRefundedAmount());
        BigDecimal refund = amount.min(refundable);
        if (refund.signum() <= 0 || !walletService.credit(payment.getCustomerId(), refund, reason, orderId, reference)) {
            return;
        }
        payment.setRefundedAmount(payment.getRefundedAmount().add(refund));
        payment.moveTo(payment.getRefundedAmount().compareTo(payment.getCapturedAmount()) >= 0
                ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
    }

    private void ensureCaptured(Payment payment) {
        switch (payment.getStatus()) {
            case HELD -> {
                gateway.capture(chargeReference(payment), payment.getAmount());
                payment.moveTo(PaymentStatus.CAPTURED);
                payment.setCapturedAmount(payment.getAmount());
            }
            case CAPTURED, PARTIALLY_REFUNDED, REFUNDED -> {
            }
            case HOLD_PENDING, FAILED, RELEASED ->
                    throw ApiException.conflict("PAYMENT_NOT_SECURED", "The payment for this order is not secured");
        }
    }

    /** Gateways that capture by payment id get that; the others get the hold reference. */
    private static String chargeReference(Payment payment) {
        return payment.getGatewayPaymentId() != null ? payment.getGatewayPaymentId() : payment.getGatewayReference();
    }

    private Payment load(UUID orderId) {
        return payments.findByOrderId(orderId).orElseThrow(() -> ApiException.notFound("Payment"));
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
                        gateway.release(chargeReference(payment));
                    }
                    payment.moveTo(PaymentStatus.RELEASED);
                }
                case CAPTURED, PARTIALLY_REFUNDED -> {
                    BigDecimal refundable = payment.getCapturedAmount().subtract(payment.getRefundedAmount());
                    if (walletService.credit(payment.getCustomerId(), refundable, reason, orderId, "refund:cancel:" + orderId)) {
                        payment.setRefundedAmount(payment.getRefundedAmount().add(refundable));
                    }
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
