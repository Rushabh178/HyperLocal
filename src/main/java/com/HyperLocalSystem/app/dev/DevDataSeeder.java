package com.HyperLocalSystem.app.dev;

import com.HyperLocalSystem.app.agent.entity.DeliveryAgent;
import com.HyperLocalSystem.app.agent.entity.VehicleType;
import com.HyperLocalSystem.app.agent.entity.VerificationStatus;
import com.HyperLocalSystem.app.agent.repository.DeliveryAgentRepository;
import com.HyperLocalSystem.app.catalog.entity.CatalogItem;
import com.HyperLocalSystem.app.catalog.entity.ItemType;
import com.HyperLocalSystem.app.catalog.repository.CatalogItemRepository;
import com.HyperLocalSystem.app.common.geo.GeoUtils;
import com.HyperLocalSystem.app.common.model.TemperatureZone;
import com.HyperLocalSystem.app.customer.entity.AddressLabel;
import com.HyperLocalSystem.app.customer.entity.Customer;
import com.HyperLocalSystem.app.customer.entity.CustomerAddress;
import com.HyperLocalSystem.app.customer.repository.CustomerAddressRepository;
import com.HyperLocalSystem.app.customer.repository.CustomerRepository;
import com.HyperLocalSystem.app.merchant.entity.ApprovalStatus;
import com.HyperLocalSystem.app.merchant.entity.Merchant;
import com.HyperLocalSystem.app.merchant.entity.MerchantType;
import com.HyperLocalSystem.app.merchant.repository.MerchantRepository;
import com.HyperLocalSystem.app.spatial.SpatialIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Demo data for local testing, only in the {@code dev} profile: open, approved restaurants and
 * Kiranas around Pune city centre (18.5204, 73.8567) with menus and groceries, one approved rider
 * and one customer with a saved address. Every account's password is {@value #PASSWORD}.
 *
 * <p>Idempotent: an account whose phone already exists is left alone, so it is safe on every start.
 * The mobile apps' "add address" map starts at the same centre, so a new address there is in range
 * of every store (restaurants within 2 km, Kiranas within the 500 m broadcast ring).
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    static final String PASSWORD = "Demo@1234";

    private final MerchantRepository merchants;
    private final CatalogItemRepository items;
    private final DeliveryAgentRepository agents;
    private final CustomerRepository customers;
    private final CustomerAddressRepository addresses;
    private final PasswordEncoder passwordEncoder;
    private final SpatialIndexService spatialIndex;

    private record Dish(String category, String name, String description, int price, boolean veg) {
    }

    private record Product(String category, String name, String brand, String packSize, int mrp, int price, TemperatureZone zone) {
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String hash = passwordEncoder.encode(PASSWORD);
        int created = 0;

        created += restaurant(hash, "+919000000001", "Spice Hub", "Ravi Kulkarni", "North Indian,Biryani", 18.5248, 73.8524, List.of(
                new Dish("Biryani", "Veg Biryani", "Basmati, saffron, seasonal vegetables", 180, true),
                new Dish("Biryani", "Chicken Biryani", "Hyderabadi dum, served with raita", 240, false),
                new Dish("Curries", "Paneer Butter Masala", null, 210, true),
                new Dish("Curries", "Dal Makhani", null, 160, true),
                new Dish("Breads", "Butter Naan", null, 40, true),
                new Dish("Breads", "Tandoori Roti", null, 25, true)));
        created += restaurant(hash, "+919000000002", "Pizza Point", "Neha Joshi", "Pizza,Italian", 18.5163, 73.8611, List.of(
                new Dish("Pizzas", "Margherita", "Tomato, mozzarella, basil", 199, true),
                new Dish("Pizzas", "Farmhouse", "Capsicum, onion, tomato, mushroom", 299, true),
                new Dish("Pizzas", "Chicken Tikka Pizza", null, 349, false),
                new Dish("Sides", "Garlic Bread", null, 99, true)));
        created += restaurant(hash, "+919000000003", "Thali House", "Kiran Shah", "Gujarati,Thali", 18.5281, 73.8602, List.of(
                new Dish("Thalis", "Veg Thali", "Two sabzis, dal, rice, 3 rotis, sweet", 150, true),
                new Dish("Thalis", "Gujarati Thali", null, 220, true),
                new Dish("Sweets", "Gulab Jamun (2 pcs)", null, 60, true)));
        created += restaurant(hash, "+919000000004", "Roll Junction", "Imran Sheikh", "Rolls,Street food", 18.5185, 73.8501, List.of(
                new Dish("Rolls", "Paneer Wrap", null, 140, true),
                new Dish("Rolls", "Egg Roll", null, 90, false),
                new Dish("Rolls", "Chicken Kathi Roll", null, 160, false)));

        List<Product> staples = List.of(
                new Product("Dairy", "Toned Milk", "Amul", "500 ml", 30, 30, TemperatureZone.COLD),
                new Product("Dairy", "Fresh Curd", "Chitale", "400 g", 45, 45, TemperatureZone.COLD),
                new Product("Dairy", "Paneer", "Gowardhan", "200 g", 95, 90, TemperatureZone.COLD),
                new Product("Dairy", "Butter", "Amul", "100 g", 60, 58, TemperatureZone.COLD),
                new Product("Bakery", "Brown Bread", "Britannia", "400 g", 50, 45, TemperatureZone.AMBIENT),
                new Product("Bakery", "Pav", "Local bakery", "6 pcs", 30, 30, TemperatureZone.AMBIENT),
                new Product("Snacks", "Potato Chips", "Lay's", "52 g", 20, 20, TemperatureZone.AMBIENT),
                new Product("Snacks", "Aloo Bhujia", "Haldiram", "200 g", 60, 55, TemperatureZone.AMBIENT),
                new Product("Beverages", "Cola", "Thums Up", "750 ml", 40, 40, TemperatureZone.COLD),
                new Product("Beverages", "Mango Juice", "Maaza", "1 L", 120, 110, TemperatureZone.COLD),
                new Product("Staples", "Basmati Rice", "India Gate", "1 kg", 160, 145, TemperatureZone.AMBIENT),
                new Product("Staples", "Toor Dal", "Tata Sampann", "1 kg", 180, 165, TemperatureZone.AMBIENT),
                new Product("Staples", "Whole Wheat Atta", "Aashirvaad", "5 kg", 290, 260, TemperatureZone.AMBIENT),
                new Product("Fruits & vegetables", "Bananas", null, "6 pcs", 48, 48, TemperatureZone.AMBIENT),
                new Product("Fruits & vegetables", "Tomatoes", null, "500 g", 25, 25, TemperatureZone.AMBIENT),
                new Product("Fruits & vegetables", "Onions", null, "1 kg", 40, 40, TemperatureZone.AMBIENT));
        // Two Kiranas list the same products (one a little dearer) so the catalogue shows the cheapest.
        created += kirana(hash, "+919000000011", "Sharma Kirana", "Suresh Sharma", 18.5219, 73.8552, staples, 0);
        created += kirana(hash, "+919000000012", "Patil General Store", "Anil Patil", 18.5190, 73.8584, staples, 2);

        created += rider(hash);
        created += customer(hash);
        if (created > 0) {
            log.info("[DEV DATA] Seeded {} demo accounts around Pune centre; password for all: {}", created, PASSWORD);
        }
    }

    private int restaurant(String hash, String phone, String name, String owner, String cuisines, double lat, double lng, List<Dish> menu) {
        if (merchants.findByPhone(phone).isPresent()) {
            return 0;
        }
        Merchant m = merchant(hash, phone, MerchantType.RESTAURANT, name, owner, lat, lng);
        m.setCuisines(cuisines);
        // 14 digits, unique per demo store.
        m.setFssaiNumber("10012345" + phone.substring(phone.length() - 6));
        merchants.save(m);
        for (Dish d : menu) {
            CatalogItem item = item(m, ItemType.FOOD, d.category(), d.name(), d.price(), TemperatureZone.HOT);
            item.setDescription(d.description());
            item.setVeg(d.veg());
            items.save(item);
        }
        return 1;
    }

    private int kirana(String hash, String phone, String name, String owner, double lat, double lng, List<Product> products, int markup) {
        if (merchants.findByPhone(phone).isPresent()) {
            return 0;
        }
        Merchant m = merchants.save(merchant(hash, phone, MerchantType.KIRANA, name, owner, lat, lng));
        for (Product p : products) {
            CatalogItem item = item(m, ItemType.GROCERY, p.category(), p.name(), Math.min(p.price() + markup, p.mrp()), p.zone());
            item.setBrand(p.brand());
            item.setPackSize(p.packSize());
            item.setMrp(BigDecimal.valueOf(p.mrp()).setScale(2));
            items.save(item);
        }
        return 1;
    }

    private Merchant merchant(String hash, String phone, MerchantType type, String name, String owner, double lat, double lng) {
        Merchant m = new Merchant();
        m.setType(type);
        m.setStoreName(name);
        m.setOwnerName(owner);
        m.setPhone(phone);
        m.setPasswordHash(hash);
        m.setPhoneVerified(true);
        m.setAddress(name + ", Shivajinagar, Pune");
        m.setPincode("411005");
        m.setLocation(GeoUtils.toPoint(lat, lng));
        m.setH3Index(spatialIndex.cellOf(lat, lng));
        m.setApprovalStatus(ApprovalStatus.APPROVED);
        m.setOpen(true);
        m.setOpeningHours("08:00-23:00");
        m.setUpiVpa(name.toLowerCase().replaceAll("[^a-z]", "") + "@upi");
        return m;
    }

    private static CatalogItem item(Merchant m, ItemType type, String category, String name, int price, TemperatureZone zone) {
        CatalogItem item = new CatalogItem();
        item.setMerchantId(m.getId());
        item.setType(type);
        item.setCategory(category);
        item.setName(name);
        item.setPrice(BigDecimal.valueOf(price).setScale(2));
        item.setZone(zone);
        item.setAvailable(true);
        return item;
    }

    /** Approved but offline; the demo driver (or partner app) puts it on duty. */
    private int rider(String hash) {
        String phone = "+919000000101";
        if (agents.findByPhone(phone).isPresent()) {
            return 0;
        }
        DeliveryAgent a = new DeliveryAgent();
        a.setName("Ravi Demo");
        a.setPhone(phone);
        a.setPasswordHash(hash);
        a.setPhoneVerified(true);
        a.setVehicleType(VehicleType.SCOOTER);
        a.setVehicleNumber("MH12AB1234");
        a.setCity("Pune");
        a.setVerificationStatus(VerificationStatus.APPROVED);
        a.setThermalBoxId("TB-DEMO01");
        a.setCurrentLocation(GeoUtils.toPoint(18.5210, 73.8540));
        a.setH3Index(spatialIndex.cellOf(18.5210, 73.8540));
        agents.save(a);
        return 1;
    }

    /** A customer with a saved home address at the centre, for password sign-in. */
    private int customer(String hash) {
        String phone = "+919876543210";
        if (customers.findByPhone(phone).isPresent()) {
            return 0;
        }
        Customer c = new Customer();
        c.setFullName("Asha Demo");
        c.setPhone(phone);
        c.setPasswordHash(hash);
        c.setPhoneVerified(true);
        customers.save(c);

        CustomerAddress a = new CustomerAddress();
        a.setCustomerId(c.getId());
        a.setLabel(AddressLabel.HOME);
        a.setHouseNumber("Flat 12");
        a.setBuildingName("Shanti Apartments");
        a.setStreet("FC Road");
        a.setArea("Shivajinagar");
        a.setCity("Pune");
        a.setState("Maharashtra");
        a.setPincode("411005");
        a.setLocation(GeoUtils.toPoint(18.5204, 73.8567));
        a.setH3Index(spatialIndex.cellOf(18.5204, 73.8567));
        a.setDefaultAddress(true);
        addresses.save(a);
        return 1;
    }
}
