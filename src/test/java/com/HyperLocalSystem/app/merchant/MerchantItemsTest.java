package com.HyperLocalSystem.app.merchant;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 2: merchants manage their menu / shelf. */
class MerchantItemsTest extends IntegrationTest {

    @Test
    void restaurantAndKiranaManageTheirItems() throws Exception {
        Session restaurant = signUpMerchant("RESTAURANT", "Spice Hub", point(0, 0), true);
        Session kirana = signUpMerchant("KIRANA", "Sharma Kirana", point(100, 0), true);

        String biryani = createItem(restaurant.token(), "Veg Biryani", "180.00", "Mains", "HOT");
        createItem(restaurant.token(), "Gulab Jamun", "60.00", "Desserts", "HOT");
        String milk = createItem(kirana.token(), "Toned Milk", "30.00", "Dairy", "COLD");

        // The type follows the store, and the list is paginated.
        String page = get("/api/v1/merchant/items?page=0&size=1", restaurant.token());
        assertThat((Integer) read(page, "$.totalItems")).isEqualTo(2);
        assertThat((List<?>) read(page, "$.items")).hasSize(1);
        assertThat((String) read(page, "$.items[0].itemType")).isEqualTo("FOOD");
        String shelf = get("/api/v1/merchant/items", kirana.token());
        assertThat((String) read(shelf, "$.items[0].itemType")).isEqualTo("GROCERY");

        String updated = put("/api/v1/merchant/items/" + biryani, restaurant.token(), """
                {"name":"Veg Dum Biryani","price":199.00,"category":"Mains","zone":"HOT","veg":true,"available":true}""");
        assertThat((String) read(updated, "$.name")).isEqualTo("Veg Dum Biryani");

        String soldOut = call(HttpMethod.PATCH, "/api/v1/merchant/items/" + milk + "/availability",
                kirana.token(), "{\"available\":false}", 200);
        assertThat((Boolean) read(soldOut, "$.available")).isFalse();

        // A store can't touch another store's items.
        call(HttpMethod.PATCH, "/api/v1/merchant/items/" + biryani + "/availability",
                kirana.token(), "{\"available\":false}", 404);
    }
}
