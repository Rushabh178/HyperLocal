package com.HyperLocalSystem.app.payment.gateway;

import com.HyperLocalSystem.app.config.AppProperties;
import com.HyperLocalSystem.app.payment.entity.PaymentStatus;
import com.HyperLocalSystem.app.security.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * DEVELOPMENT GATEWAY (only with the "dev" profile, and "razorpay" off). The outcome of a hold is
 * {@code payments.fake.default-outcome} (SUCCESS), and a request can pick it with the header
 * {@code X-Fake-Payment: SUCCESS | PENDING | FAIL}. The header is checked against the dev profile again,
 * so it stays ignored even if this class is ever wired up outside dev.
 * PENDING waits for a webhook {@code {"eventId","reference","type"}} signed with
 * HMAC-SHA256(app.payment.webhook-secret), hex, in the X-Signature header. Capture / release /
 * refund just log.
 */
@Slf4j
@Component
@Profile("dev & !razorpay")
public class FakePaymentGateway implements PaymentGateway {

    public static final String OUTCOME_HEADER = "X-Fake-Payment";

    public enum Outcome { SUCCESS, PENDING, FAIL }

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final Outcome defaultOutcome;
    private final boolean headerAllowed;

    public FakePaymentGateway(AppProperties properties, ObjectMapper objectMapper, Environment environment,
                              @Value("${payments.fake.default-outcome:SUCCESS}") Outcome defaultOutcome) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.defaultOutcome = defaultOutcome;
        this.headerAllowed = environment.matchesProfiles("dev");
    }

    @Override
    public String name() {
        return "FAKE";
    }

    @Override
    public HoldResult createHold(UUID paymentId, BigDecimal amount) {
        String reference = "fake_hold_" + paymentId.toString().replace("-", "");
        String intent = "upi://pay?pa=hyperlocal@fakebank&pn=HyperLocal&am=%s&cu=INR&tr=%s".formatted(amount.toPlainString(), reference);
        Outcome outcome = outcome();
        log.info("[FAKE PAYMENT] hold {} for ₹{}: {}", reference, amount, outcome);
        PaymentStatus status = switch (outcome) {
            case SUCCESS -> PaymentStatus.HELD;
            case PENDING -> PaymentStatus.HOLD_PENDING;
            case FAIL -> PaymentStatus.FAILED;
        };
        return new HoldResult(reference, status, intent, Instant.now().plus(properties.payment().holdTtl()));
    }

    /** The X-Fake-Payment header of the current request (dev profile only), else the configured default. */
    private Outcome outcome() {
        if (headerAllowed && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request) {
            String header = request.getRequest().getHeader(OUTCOME_HEADER);
            if (header != null && !header.isBlank()) {
                try {
                    return Outcome.valueOf(header.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    log.warn("[FAKE PAYMENT] unknown {} value '{}', using {}", OUTCOME_HEADER, header, defaultOutcome);
                }
            }
        }
        return defaultOutcome;
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
        return new WebhookEvent(json.path("eventId").asString(), json.path("reference").asString(), type, null);
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
