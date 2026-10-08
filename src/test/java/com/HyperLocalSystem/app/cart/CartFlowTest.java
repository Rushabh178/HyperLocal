package com.HyperLocalSystem.app.cart;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 4: one cart with groceries and food, the bill rules and the "add a meal" nudge. */
class CartFlowTest extends IntegrationTest {

    @Test
    void groceryAndFoodInOneCart() throws Exception {
        Session spiceHub = signUpMerchant("RESTAURANT", "Spice Hub", point(300, 0), true);
        Session pizzeria = signUpMerchant("RESTAURANT", "Pizza Point", point(0, 500), true);
        Session kirana = signUpMerchant("KIRANA", "Sharma Kirana", point(200, 0), true);
        String biryani = createItem(spiceHub.token(), "Veg Biryani", "180.00", "Mains", "HOT");
        String pizza = createItem(pizzeria.token(), "Farmhouse Pizza", "250.00", "Pizza", "HOT");
        String milk = createItem(kirana.token(), "Toned Milk", "30.00", "Dairy", "COLD");

        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, 0));

        // Grocery-only cart under ₹150: delivery fee charged and meals suggested.
        String cart = post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"GROCERY\",\"itemId\":\"%s\",\"quantity\":2}".formatted(milk));
        assertThat((Double) read(cart, "$.bill.grocerySubtotal")).isEqualTo(60.0);
        assertThat((Double) read(cart, "$.bill.deliveryFee")).isEqualTo(25.0);
        assertThat((List<String>) read(cart, "$.nudge.suggestedMeals[*].name")).containsExactly("Farmhouse Pizza", "Veg Biryani");

        // Adding food waives delivery and removes the nudge: 180 + 60 + 0 + 10 fee + 9 GST = 259.
        cart = post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(biryani, spiceHub.id()));
        assertThat((Double) read(cart, "$.bill.total")).isEqualTo(259.0);
        assertThat((Object) read(cart, "$.nudge")).isNull();

        // Food from another restaurant replaces the food section.
        cart = post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(pizza, pizzeria.id()));
        assertThat((String) read(cart, "$.restaurantId")).isEqualTo(pizzeria.id().toString());
        assertThat((List<String>) read(cart, "$.foodItems[*].name")).containsExactly("Farmhouse Pizza");

        String milkLine = read(cart, "$.groceryItems[0].cartItemId");
        cart = call(HttpMethod.PATCH, "/api/v1/customer/cart/items/" + milkLine, customer.token(), "{\"quantity\":3}", 200);
        assertThat((Double) read(cart, "$.bill.grocerySubtotal")).isEqualTo(90.0);

        cart = put("/api/v1/customer/cart/address", customer.token(), "{\"addressId\":\"%s\"}".formatted(home));
        assertThat((String) read(cart, "$.deliveryAddressId")).isEqualTo(home);
        String farAway = addAddress(customer.token(), point(6000, 0));
        String refused = call(HttpMethod.PUT, "/api/v1/customer/cart/address", customer.token(),
                "{\"addressId\":\"%s\"}".formatted(farAway), 400);
        assertThat((String) read(refused, "$.code")).isEqualTo("ADDRESS_NOT_SERVICEABLE");

        // Removing the food brings the nudge back; clearing empties everything.
        String pizzaLine = read(cart, "$.foodItems[0].cartItemId");
        cart = call(HttpMethod.DELETE, "/api/v1/customer/cart/items/" + pizzaLine, customer.token(), null, 200);
        assertThat((Object) read(cart, "$.restaurantId")).isNull();
        assertThat((String) read(get("/api/v1/customer/cart/nudges", customer.token()), "$.message")).isNotNull();

        call(HttpMethod.DELETE, "/api/v1/customer/cart", customer.token(), null, 204);
        assertThat((Double) read(get("/api/v1/customer/cart", customer.token()), "$.bill.total")).isEqualTo(0.0);
    }
}
