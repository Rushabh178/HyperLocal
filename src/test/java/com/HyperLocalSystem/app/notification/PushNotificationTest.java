package com.HyperLocalSystem.app.notification;

import com.HyperLocalSystem.app.notification.push.PushMessage;
import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** §2: device registration, pushes per role with the envelope, token moves, logout removes it. */
class PushNotificationTest extends IntegrationTest {

    @Test
    void pushesReachTheRightPhones() throws Exception {
        Session restaurant = signUpMerchant("RESTAURANT", "Spice Hub", point(0, 0), true);
        Session rider = onlineRider(point(100, 0));
        String dish = createItem(restaurant.token(), "Paneer Roll", "120.00", "Rolls", "HOT");
        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, -300));

        String customerPhone = "fcm-customer-" + UUID.randomUUID();
        String riderPhone = "fcm-rider-" + UUID.randomUUID();
        String restaurantPhone = "fcm-restaurant-" + UUID.randomUUID();
        register("/api/v1/customer/devices", customer, customerPhone, "ANDROID");
        register("/api/v1/agent/devices", rider, riderPhone, "IOS");
        register("/api/v1/merchant/devices", restaurant, restaurantPhone, "ANDROID");

        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":2}".formatted(dish, restaurant.id()));
        String orderId = read(call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(home), 201), "$.orderId");

        // Restaurant: a normal notification with the envelope as data.
        PushMessage newOrder = pushes.waitFor(restaurantPhone, "NEW_ORDER");
        assertThat(newOrder.title()).isEqualTo("New order");
        assertThat(newOrder.body()).isEqualTo("2 items · ₹240.00. Accept it now.");
        assertThat(newOrder.envelope().sentAt()).isNotNull();

        // Rider: the offer is data-only and expires with the offer.
        PushMessage offer = pushes.waitFor(riderPhone, "OFFER");
        assertThat(offer.isDataOnly()).isTrue();
        assertThat(offer.expiresAt()).isAfter(Instant.now().minusSeconds(1));

        // Customer: order status changes.
        PushMessage status = pushes.waitFor(customerPhone, "ORDER_STATUS");
        assertThat(status.body()).isEqualTo("Your order is confirmed");

        // The same phone signs in as another customer: the token moves to them.
        Session other = signUpCustomer();
        register("/api/v1/customer/devices", other, customerPhone, "ANDROID");
        assertThat(jdbc.queryForObject("select user_id from device_tokens where token = ?", UUID.class, customerPhone))
                .isEqualTo(other.id());

        // Logout with the device token removes it in the same call.
        String refreshToken = read(post("/api/v1/agent/auth/login", null,
                "{\"identifier\":\"%s\",\"password\":\"secret-pass-1\"}".formatted(rider.phone())), "$.refreshToken");
        call(HttpMethod.POST, "/api/v1/agent/auth/logout", null,
                "{\"refreshToken\":\"%s\",\"deviceToken\":\"%s\"}".formatted(refreshToken, riderPhone), 204);
        assertThat(jdbc.queryForObject("select count(*) from device_tokens where token = ?", Integer.class, riderPhone)).isZero();
        assertThat(orderId).isNotNull();
    }

    private void register(String url, Session session, String token, String platform) throws Exception {
        call(HttpMethod.POST, url, session.token(), "{\"token\":\"%s\",\"platform\":\"%s\"}".formatted(token, platform), 204);
    }
}
