package com.HyperLocalSystem.app.payment.gateway;

import com.HyperLocalSystem.app.config.AppProperties;
import com.HyperLocalSystem.app.payment.entity.PaymentStatus;
import com.HyperLocalSystem.app.security.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * DEVELOPMENT ONLY. Every hold succeeds immediately; capture/release/refund just log. Webhooks are
 * {@code {"eventId","reference","type"}} signed with HMAC-SHA256(app.payment.webhook-secret) in
 * the X-Signature header (hex), so the callback path can be exercised end to end.
 */
@Slf4j
@Component
@Profile("!razorpay")
public class FakePaymentGateway implements PaymentGateway {

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public FakePaymentGateway(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "FAKE";
    }

    @Override
    public HoldResult createHold(UUID paymentId, BigDecimal amount, String upiVpa) {
        String reference = "fake_hold_" + paymentId.toString().replace("-", "");
        String intent = "upi://pay?pa=hyperlocal@fakebank&pn=HyperLocal&am=%s&cu=INR&tr=%s".formatted(amount.toPlainString(), reference);
        log.info("[FAKE PAYMENT] hold {} for ₹{} auto-approved", reference, amount);
        return new HoldResult(reference, PaymentStatus.HELD, intent, Instant.now().plus(properties.payment().holdTtl()));
    }

    @Override
    public void capture(String reference, BigDecimal amount) {
        log.info("[FAKE PAYMENT] captured ₹{} on {}", amount, reference);
    }

    @Override
    public void release(String reference) {
        log.info("[FAKE PAYMENT] released hold {}", reference);
    }

    @Override
    public void refund(String reference, BigDecimal amount) {
        log.info("[FAKE PAYMENT] refunded ₹{} on {}", amount, reference);
    }

    @Override
    public boolean verifySignature(String rawBody, String signature) {
        return signature != null && Hashing.constantTimeEquals(sign(rawBody), signature.trim().toLowerCase());
    }

    @Override
    public WebhookEvent parseWebhook(String rawBody) {
        JsonNode json = objectMapper.readTree(rawBody);
        WebhookType type = switch (json.path("type").asString("")) {
            case "HOLD_CONFIRMED" -> WebhookType.HOLD_CONFIRMED;
            case "HOLD_FAILED" -> WebhookType.HOLD_FAILED;
            default -> WebhookType.IGNORED;
        };
        return new WebhookEvent(json.path("eventId").asString(), json.path("reference").asString(), type);
    }

    /** Hex HMAC-SHA256 of the body; public so tests and local tools can sign fake callbacks. */
    public String sign(String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.payment().webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }
}
