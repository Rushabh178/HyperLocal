package com.HyperLocalSystem.app.payment;

import com.HyperLocalSystem.app.payment.gateway.FakePaymentGateway;
import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §8: checkout returns upiIntentUrl only while there is a hold to approve; the app learns the
 * outcome from PAYMENT_CONFIRMED / PAYMENT_FAILED. Outcomes come from the dev-only X-Fake-Payment header.
 */
class PaymentOutcomeTest extends IntegrationTest {

    @Autowired
    FakePaymentGateway fakeGateway;

    @Test
    void checkoutOutcomesReachTheApp() throws Exception {
        Session restaurant = signUpMerchant("RESTAURANT", "Spice Hub", point(300, 0), true);
        String dish = createItem(restaurant.token(), "Veg Thali", "150.00", "Thalis", "HOT");
        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, 0));

        // SUCCESS (the default): confirmed at once, nothing to open.
        String success = checkout(customer, restaurant.id(), dish, home, "SUCCESS");
        assertThat((String) read(success, "$.status")).isEqualTo("CONFIRMED");
        assertThat((Object) read(success, "$.upiIntentUrl")).isNull();
        messages.waitFor(customer.id(), "/queue/tracking", "PAYMENT_CONFIRMED", "$.data.orderId", read(success, "$.orderId"));

        // PENDING: the app opens the intent and waits; the gateway's callback confirms it.
        String pending = checkout(customer, restaurant.id(), dish, home, "PENDING");
        String pendingId = read(pending, "$.orderId");
        assertThat((String) read(pending, "$.status")).isEqualTo("PENDING");
        assertThat((String) read(pending, "$.upiIntentUrl")).startsWith("upi://pay?");
        String reference = jdbc.queryForObject("select gateway_reference from payments where order_id = ?", String.class, UUID.fromString(pendingId));
        String event = "{\"eventId\":\"evt-%s\",\"reference\":\"%s\",\"type\":\"HOLD_CONFIRMED\"}".formatted(UUID.randomUUID(), reference);
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments/webhook")
                .contentType("application/json").header("X-Signature", fakeGateway.sign(event)).content(event));
        String confirmed = messages.waitFor(customer.id(), "/queue/tracking", "PAYMENT_CONFIRMED", "$.data.orderId", pendingId);
        assertThat((String) read(confirmed, "$.data.orderStatus")).isEqualTo("CONFIRMED");

        // FAIL: PAYMENT_FAILED, the order waits for a retry, which then goes through.
        String failed = checkout(customer, restaurant.id(), dish, home, "FAIL");
        String failedId = read(failed, "$.orderId");
        assertThat((String) read(failed, "$.payment.status")).isEqualTo("FAILED");
        assertThat((Object) read(failed, "$.upiIntentUrl")).isNull();
        String failure = messages.waitFor(customer.id(), "/queue/tracking", "PAYMENT_FAILED", "$.data.orderId", failedId);
        assertThat((String) read(failure, "$.data.orderStatus")).isEqualTo("PENDING");
        assertThat((String) read(post("/api/v1/payments/orders/" + failedId + "/upi-hold", customer.token(), null), "$.status"))
                .isEqualTo("HELD");
        assertThat((String) read(get("/api/v1/customer/orders/" + failedId, customer.token()), "$.status")).isEqualTo("CONFIRMED");
    }

    private String checkout(Session customer, UUID restaurantId, String dish, String addressId, String outcome) throws Exception {
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(dish, restaurantId));
        return postWithHeader("/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(addressId), FakePaymentGateway.OUTCOME_HEADER, outcome, 201);
    }
}
