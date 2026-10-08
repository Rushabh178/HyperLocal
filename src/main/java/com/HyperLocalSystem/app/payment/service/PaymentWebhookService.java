package com.HyperLocalSystem.app.payment.service;

import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.order.entity.OrderStatus;
import com.HyperLocalSystem.app.order.entity.UnifiedOrder;
import com.HyperLocalSystem.app.order.repository.UnifiedOrderRepository;
import com.HyperLocalSystem.app.order.service.OrderLifecycle;
import com.HyperLocalSystem.app.payment.dto.PaymentDtos.PaymentHoldResponse;
import com.HyperLocalSystem.app.payment.entity.Payment;
import com.HyperLocalSystem.app.payment.entity.PaymentWebhookEvent;
import com.HyperLocalSystem.app.payment.gateway.PaymentGateway;
import com.HyperLocalSystem.app.payment.gateway.PaymentGateway.WebhookEvent;
import com.HyperLocalSystem.app.payment.repository.PaymentWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Connects gateway outcomes to the order: a confirmed hold confirms the order (which starts
 * dispatch), a failed one leaves it PENDING for a retry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private final PaymentGateway gateway;
    private final PaymentService paymentService;
    private final PaymentWebhookEventRepository webhookEvents;
    private final UnifiedOrderRepository orders;
    private final OrderLifecycle lifecycle;

    /**
     * Verifies the signature before reading anything from the body, then applies the event once:
     * a redelivered event id is acknowledged without doing anything.
     */
    @Transactional
    public void handle(String signature, String rawBody) {
        if (signature == null || !gateway.verifySignature(rawBody, signature)) {
            throw ApiException.unauthorized("INVALID_SIGNATURE", "Webhook signature is missing or wrong");
        }
        WebhookEvent event;
        try {
            event = gateway.parseWebhook(rawBody);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("WEBHOOK_INVALID", "Webhook body could not be read");
        }
        if (event.eventId() == null || event.eventId().isBlank()) {
            throw ApiException.badRequest("WEBHOOK_INVALID", "Webhook has no event id");
        }
        if (webhookEvents.existsById(event.eventId())) {
            log.info("Payment webhook {} already processed", event.eventId());
            return;
        }
        // A concurrent duplicate delivery hits the primary key and gets 409; the gateway retries and sees it processed.
        webhookEvents.saveAndFlush(new PaymentWebhookEvent(event.eventId(), event.reference(), event.type().name()));
        paymentService.applyWebhook(event).ifPresent(this::confirmOrder);
    }

    /** Retry a failed UPI hold for an order still waiting for payment. */
    @Transactional
    public PaymentHoldResponse retryHold(UUID customerId, UUID orderId) {
        UnifiedOrder order = orders.findByIdAndCustomerId(orderId, customerId).orElseThrow(() -> ApiException.notFound("Order"));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw ApiException.conflict("ORDER_NOT_AWAITING_PAYMENT", "This order is not waiting for payment");
        }
        Payment payment = paymentService.retryHold(customerId, orderId);
        if (payment.isSecured()) {
            confirmOrder(payment);
        }
        return PaymentService.toResponse(payment);
    }

    private void confirmOrder(Payment payment) {
        orders.findById(payment.getOrderId())
                .filter(order -> order.getStatus() == OrderStatus.PENDING)
                .ifPresent(lifecycle::confirm);
        // TODO(edge-case): a hold confirmed after the customer cancelled should be released at the gateway.
        // TODO(edge-case): cancel orders whose hold never arrives (PENDING past app.payment.hold-ttl).
    }
}
