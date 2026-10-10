package com.HyperLocalSystem.app.catalog;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 3: a customer browses restaurants, a menu, the grocery catalogue and search around their pin. */
class CatalogBrowsingTest extends IntegrationTest {

    @Test
    void browseWithinRange() throws Exception {
        Session spiceHub = signUpMerchant("RESTAURANT", "Spice Hub", point(300, 0), true);
        Session closed = signUpMerchant("RESTAURANT", "Late Night Diner", point(1000, 0), false);
        jdbc.update("update merchants set approval_status = 'APPROVED' where merchant_id = ?", closed.id());
        signUpMerchant("RESTAURANT", "Far Away Dhaba", point(7000, 0), true);
        Session kirana1 = signUpMerchant("KIRANA", "Sharma Kirana", point(0, 500), true);
        Session kirana2 = signUpMerchant("KIRANA", "Gupta Stores", point(0, 800), true);

        createItem(spiceHub.token(), "Veg Biryani", "180.00", "Mains", "HOT");
        createItem(spiceHub.token(), "Gulab Jamun", "60.00", "Desserts", "HOT");
        createItem(kirana1.token(), "Toned Milk", "30.00", "Dairy", "COLD");
        createItem(kirana1.token(), "Brown Bread", "45.00", "Bakery", "AMBIENT");
        createItem(kirana2.token(), "Toned Milk", "28.00", "Dairy", "COLD");

        // Restaurants: the 7 km one is out of range (6 km); open ones come before closed ones.
        String restaurants = get("/api/v1/catalog/restaurants?" + latLng(0, 0), null);
        assertThat((Integer) read(restaurants, "$.totalItems")).isEqualTo(2);
        assertThat((String) read(restaurants, "$.items[0].name")).isEqualTo("Spice Hub");
        assertThat((Boolean) read(restaurants, "$.items[1].open")).isFalse();
        assertThat((Integer) read(restaurants, "$.items[0].etaMinutes")).isGreaterThan(15);
        assertThat((Integer) read(get("/api/v1/catalog/restaurants?cuisine=thai&" + latLng(0, 0), null), "$.totalItems"))
                .isZero();

        String menu = get("/api/v1/catalog/restaurants/" + spiceHub.id() + "/menu", null);
        assertThat((List<String>) read(menu, "$.categories[*].name")).containsExactly("Desserts", "Mains");

        // Groceries: the same product from two Kiranas shows once, at the cheaper price.
        String groceries = get("/api/v1/catalog/groceries?" + latLng(0, 0), null);
        assertThat((Integer) read(groceries, "$.totalItems")).isEqualTo(2);
        assertThat((List<Double>) read(groceries, "$.items[?(@.name == 'Toned Milk')].price")).containsExactly(28.0);

        String search = get("/api/v1/catalog/search?q=biryani&" + latLng(0, 0), null);
        assertThat((List<?>) read(search, "$.dishes")).hasSize(1);
        assertThat((List<?>) read(get("/api/v1/catalog/search?q=milk&" + latLng(0, 0), null), "$.groceries")).hasSize(1);

        // Serviceability counts approved stores in range and is false far away.
        Session customer = signUpCustomer();
        String here = post("/api/v1/customer/addresses/serviceability", customer.token(), point(0, 0));
        assertThat((Boolean) read(here, "$.serviceable")).isTrue();
        assertThat((Integer) read(here, "$.nearbyRestaurants")).isEqualTo(2);
        assertThat((Integer) read(here, "$.nearbyKiranaStores")).isEqualTo(2);
        String nowhere = post("/api/v1/customer/addresses/serviceability", customer.token(), point(50_000, 50_000));
        assertThat((Boolean) read(nowhere, "$.serviceable")).isFalse();
    }
}
