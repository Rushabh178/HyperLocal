package com.HyperLocalSystem.app.payment.repository;

import com.HyperLocalSystem.app.payment.entity.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, String> {
}
