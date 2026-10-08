package com.HyperLocalSystem.app.order;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 5: checkout (UPI hold and wallet), order details, timeline, cancel. */
class CheckoutFlowTest extends IntegrationTest {

    @Test
    void checkoutViewAndCancel() throws Exception {
        Session spiceHub = signUpMerchant("RESTAURANT", "Spice Hub", point(300, 0), true);
        Session kirana = signUpMerchant("KIRANA", "Sharma Kirana", point(200, 0), true);
        String biryani = createItem(spiceHub.token(), "Veg Biryani", "180.00", "Mains", "HOT");
        String milk = createItem(kirana.token(), "Toned Milk", "30.00", "Dairy", "COLD");
        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, 0));

        fillCart(customer, biryani, spiceHub.id(), milk);
        String checkout = postWithHeader("/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(home), "Idempotency-Key", "tap-1", 201);
        String orderId = read(checkout, "$.orderId");
        // The fake gateway approves the hold at once, so the order is confirmed straight away.
        assertThat((String) read(checkout, "$.status")).isEqualTo("CONFIRMED");
        assertThat((String) read(checkout, "$.payment.status")).isEqualTo("HELD");
        assertThat((Double) read(checkout, "$.payableAmount")).isEqualTo(259.0);

        // A retry with the same key returns the same order instead of charging twice.
        String retry = postWithHeader("/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(home), "Idempotency-Key", "tap-1", 201);
        assertThat((String) read(retry, "$.orderId")).isEqualTo(orderId);
        assertThat((List<?>) read(get("/api/v1/customer/cart", customer.token()), "$.foodItems")).isEmpty();

        String detail = get("/api/v1/customer/orders/" + orderId, customer.token());
        assertThat((List<?>) read(detail, "$.foodItems")).hasSize(1);
        assertThat((List<?>) read(detail, "$.groceryItems")).hasSize(1);
        assertThat((String) read(detail, "$.deliveryOtp")).matches("\\d{4}");
        assertThat((Integer) read(get("/api/v1/customer/orders?status=CONFIRMED", customer.token()), "$.totalItems")).isEqualTo(1);

        String timeline = get("/api/v1/customer/orders/" + orderId + "/timeline", customer.token());
        assertThat((List<String>) read(timeline, "$.stops[*].type")).containsExactly("KIRANA", "RESTAURANT", "CUSTOMER");

        // Can't delete the account mid-order; another customer can't see the order.
        String blocked = call(HttpMethod.DELETE, "/api/v1/customer/me", customer.token(), null, 409);
        assertThat((String) read(blocked, "$.code")).isEqualTo("ORDER_IN_PROGRESS");
        call(HttpMethod.GET, "/api/v1/customer/orders/" + orderId, signUpCustomer().token(), null, 404);

        String cancelled = post("/api/v1/customer/orders/" + orderId + "/cancel", customer.token(), "{\"reason\":\"Changed my mind\"}");
        assertThat((String) read(cancelled, "$.status")).isEqualTo("CANCELLED");
        assertThat((String) read(get("/api/v1/payments/orders/" + orderId, customer.token()), "$.status")).isEqualTo("RELEASED");
        call(HttpMethod.POST, "/api/v1/customer/orders/" + orderId + "/rating", customer.token(), "{\"deliveryRating\":5}", 409);
    }

    @Test
    void walletPaymentAndRefundOnCancel() throws Exception {
        Session spiceHub = signUpMerchant("RESTAURANT", "Spice Hub", point(300, 0), true);
        String biryani = createItem(spiceHub.token(), "Veg Biryani", "180.00", "Mains", "HOT");
        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, 0));
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(biryani, spiceHub.id()));

        // Empty wallet: refused, and nothing is created (the cart is still there).
        String refused = call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"WALLET\"}".formatted(home), 409);
        assertThat((String) read(refused, "$.code")).isEqualTo("INSUFFICIENT_WALLET_BALANCE");
        assertThat((Integer) read(get("/api/v1/customer/orders", customer.token()), "$.totalItems")).isZero();

        jdbc.update("insert into wallet_entries (entry_id, customer_id, type, amount, reason, reference, created_at) "
                + "values (?, ?, 'CREDIT', 500, 'Welcome credit', ?, now())", UUID.randomUUID(), customer.id(), "promo:" + customer.id());
        String checkout = call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"WALLET\"}".formatted(home), 201);
        assertThat((String) read(checkout, "$.payment.status")).isEqualTo("CAPTURED");
        // 180 food + 10 convenience + 9 GST = 199
        assertThat((Double) read(get("/api/v1/customer/wallet", customer.token()), "$.balance")).isEqualTo(301.0);

        post("/api/v1/customer/orders/" + read(checkout, "$.orderId") + "/cancel", customer.token(), "{\"reason\":\"Ordered by mistake\"}");
        assertThat((Double) read(get("/api/v1/customer/wallet", customer.token()), "$.balance")).isEqualTo(500.0);
        assertThat((Integer) read(get("/api/v1/customer/wallet/transactions", customer.token()), "$.totalItems")).isEqualTo(3);
    }

    private void fillCart(Session customer, String foodItem, UUID restaurantId, String groceryItem) throws Exception {
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(foodItem, restaurantId));
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"GROCERY\",\"itemId\":\"%s\",\"quantity\":2}".formatted(groceryItem));
    }
}
