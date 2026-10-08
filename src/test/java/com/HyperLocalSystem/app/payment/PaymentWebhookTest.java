package com.HyperLocalSystem.app.payment;

import com.HyperLocalSystem.app.payment.gateway.FakePaymentGateway;
import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 9: a pending UPI hold is confirmed by a signed, idempotent webhook; a failed hold is retried. */
class PaymentWebhookTest extends IntegrationTest {

    @Autowired
    FakePaymentGateway fakeGateway;

    @Test
    void webhookConfirmsHoldOnceAndFailedHoldCanBeRetried() throws Exception {
        Session restaurant = signUpMerchant("RESTAURANT", "Spice Hub", point(300, 0), true);
        String dish = createItem(restaurant.token(), "Veg Thali", "150.00", "Thalis", "HOT");
        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, 0));

        // X-Fake-Payment: PENDING makes the fake gateway wait for a webhook, like a real UPI app approval.
        String orderId = pendingOrder(customer, restaurant.id(), dish, home);
        String payment = get("/api/v1/payments/orders/" + orderId, customer.token());
        assertThat((String) read(payment, "$.status")).isEqualTo("HOLD_PENDING");
        assertThat((Integer) read(get("/api/v1/merchant/orders", restaurant.token()), "$.totalItems")).isZero();

        String reference = jdbc.queryForObject("select gateway_reference from payments where order_id = ?", String.class, UUID.fromString(orderId));
        String confirmed = "{\"eventId\":\"evt-%s\",\"reference\":\"%s\",\"type\":\"HOLD_CONFIRMED\"}".formatted(UUID.randomUUID(), reference);

        assertThat(webhook(confirmed, "not-the-signature")).isEqualTo(401);
        assertThat((String) read(get("/api/v1/customer/orders/" + orderId, customer.token()), "$.status")).isEqualTo("PENDING");

        assertThat(webhook(confirmed, fakeGateway.sign(confirmed))).isEqualTo(200);
        assertThat(webhook(confirmed, fakeGateway.sign(confirmed))).as("redelivery is a no-op").isEqualTo(200);
        assertThat((String) read(get("/api/v1/customer/orders/" + orderId, customer.token()), "$.status")).isEqualTo("CONFIRMED");
        assertThat((String) read(get("/api/v1/payments/orders/" + orderId, customer.token()), "$.status")).isEqualTo("HELD");
        assertThat((String) read(get("/api/v1/merchant/orders", restaurant.token()), "$.items[0].status")).isEqualTo("NEW");

        // A failed hold leaves the order PENDING; the customer retries and the order goes through.
        String second = pendingOrder(customer, restaurant.id(), dish, home);
        String secondRef = jdbc.queryForObject("select gateway_reference from payments where order_id = ?", String.class, UUID.fromString(second));
        String failed = "{\"eventId\":\"evt-%s\",\"reference\":\"%s\",\"type\":\"HOLD_FAILED\"}".formatted(UUID.randomUUID(), secondRef);
        assertThat(webhook(failed, fakeGateway.sign(failed))).isEqualTo(200);
        assertThat((String) read(get("/api/v1/payments/orders/" + second, customer.token()), "$.status")).isEqualTo("FAILED");

        String retried = post("/api/v1/payments/orders/" + second + "/upi-hold", customer.token(), null);
        assertThat((String) read(retried, "$.status")).isEqualTo("HELD");
        assertThat((String) read(get("/api/v1/customer/orders/" + second, customer.token()), "$.status")).isEqualTo("CONFIRMED");
        call(HttpMethod.POST, "/api/v1/payments/orders/" + second + "/upi-hold", customer.token(), null, 409);
    }

    private String pendingOrder(Session customer, UUID restaurantId, String dish, String addressId) throws Exception {
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(dish, restaurantId));
        String checkout = postWithHeader("/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(addressId), FakePaymentGateway.OUTCOME_HEADER, "PENDING", 201);
        assertThat((String) read(checkout, "$.status")).isEqualTo("PENDING");
        return read(checkout, "$.orderId");
    }

    /** The gateway calls without a JWT; only the signature header authenticates it. */
    private int webhook(String body, String signature) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments/webhook")
                .contentType(MediaType.APPLICATION_JSON).header("X-Signature", signature).content(body))
                .andReturn().getResponse().getStatus();
    }
}
