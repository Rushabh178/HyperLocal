package com.HyperLocalSystem.app.payment.gateway;

import com.HyperLocalSystem.app.config.AppProperties;
import com.HyperLocalSystem.app.payment.entity.PaymentStatus;
import com.HyperLocalSystem.app.security.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * SKELETON for Razorpay ("razorpay" profile). Hold = an order with manual capture
 * ({@code payment_capture: 0}); the app's Razorpay Checkout / UPI intent authorises it and the
 * {@code payment.authorized} webhook confirms the hold and reveals the payment id that capture and
 * refund need. Amounts are sent in paise.
 * <p>
 * Not yet production-ready:
 * TODO(edge-case): UPI one-time mandates (reserve-pay) for long holds instead of a plain authorisation.
 * TODO(edge-case): map Razorpay error responses to ApiExceptions; retries with idempotency headers.
 */
@Slf4j
@Component
@Profile("razorpay")
public class RazorpayPaymentGateway implements PaymentGateway {

    private final RestClient client;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public RazorpayPaymentGateway(AppProperties properties, ObjectMapper objectMapper) {
        AppProperties.Razorpay config = properties.payment().razorpay();
        String credentials = Base64.getEncoder()
                .encodeToString((config.keyId() + ":" + config.keySecret()).getBytes(StandardCharsets.UTF_8));
        this.client = RestClient.builder()
                .baseUrl(config.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "RAZORPAY";
    }

    @Override
    public HoldResult createHold(UUID paymentId, BigDecimal amount, String upiVpa) {
        JsonNode order = client.post().uri("/orders").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", paise(amount), "currency", "INR", "receipt", paymentId.toString(), "payment_capture", 0))
                .retrieve().body(JsonNode.class);
        return new HoldResult(order.path("id").asString(), PaymentStatus.HOLD_PENDING, null,
                Instant.now().plus(properties.payment().holdTtl()));
    }

    /** {@code reference} must be the Razorpay payment id (pay_...), stored from the webhook. */
    @Override
    public void capture(String reference, BigDecimal amount) {
        client.post().uri("/payments/{id}/capture", reference).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", paise(amount), "currency", "INR"))
                .retrieve().toBodilessEntity();
    }

    /** Razorpay has no void call: an authorised payment that is never captured is refunded automatically. */
    @Override
    public void release(String reference) {
        log.info("Razorpay authorisation {} left uncaptured; Razorpay refunds it automatically", reference);
    }

    @Override
    public void refund(String reference, BigDecimal amount) {
        client.post().uri("/payments/{id}/refund", reference).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", paise(amount)))
                .retrieve().toBodilessEntity();
    }

    /** X-Razorpay-Signature = hex HMAC-SHA256 of the raw body with the webhook secret. */
    @Override
    public boolean verifySignature(String rawBody, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.payment().webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            return Hashing.constantTimeEquals(expected, signature.trim());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    /**
     * {@code payment.authorized} → hold confirmed, {@code payment.failed} → failed. The event id is
     * event + payment id, the same on every redelivery.
     * TODO(edge-case): prefer the X-Razorpay-Event-Id header once the controller passes headers through.
     */
    @Override
    public WebhookEvent parseWebhook(String rawBody) {
        JsonNode json = objectMapper.readTree(rawBody);
        String event = json.path("event").asString("");
        JsonNode payment = json.path("payload").path("payment").path("entity");
        WebhookType type = switch (event) {
            case "payment.authorized" -> WebhookType.HOLD_CONFIRMED;
            case "payment.failed" -> WebhookType.HOLD_FAILED;
            default -> WebhookType.IGNORED;
        };
        String paymentId = payment.path("id").asString(null);
        return new WebhookEvent(event + ":" + paymentId, payment.path("order_id").asString(null), type, paymentId);
    }

    private static long paise(BigDecimal rupees) {
        return rupees.movePointRight(2).longValueExact();
    }
}
