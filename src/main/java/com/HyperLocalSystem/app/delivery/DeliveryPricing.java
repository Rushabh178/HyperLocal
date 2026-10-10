package com.HyperLocalSystem.app.delivery;

import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.config.AppProperties;
import com.HyperLocalSystem.app.merchant.entity.MerchantType;
import com.HyperLocalSystem.app.routing.RoutingClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Delivery range and distance pricing ({@code app.delivery.*}). Two different distances, on purpose:
 * <ul>
 *   <li><b>Visibility</b> (which stores a customer can see / order from) uses the straight line from the
 *       delivery address to the store: {@link #radiusMeters} (restaurants 6 km, Kiranas 2 km).</li>
 *   <li><b>The fee</b> uses the road distance from the store to the delivery address ({@link #roadKm}, via
 *       {@link RoutingClient}): free up to {@code free-delivery-km}, then the matching slab.</li>
 * </ul>
 */
@Component
public class DeliveryPricing {

    /** One fee band: more than {@code fromKm} and at most {@code toKm} costs {@code fee}. */
    public record Slab(double fromKm, double toKm, BigDecimal fee) {
    }

    private final AppProperties.Delivery delivery;
    private final RoutingClient routingClient;
    private final List<Slab> slabs;

    public DeliveryPricing(AppProperties properties, RoutingClient routingClient) {
        this.delivery = properties.delivery();
        this.routingClient = routingClient;
        this.slabs = parse(delivery.feeSlabs());
    }

    /** {@code "2-3:15,3-4:25"} → slabs ordered by distance. */
    static List<Slab> parse(String spec) {
        return Arrays.stream(spec.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(part -> {
                    String[] rangeAndFee = part.split(":");
                    String[] range = rangeAndFee[0].split("-");
                    return new Slab(Double.parseDouble(range[0].trim()), Double.parseDouble(range[1].trim()),
                            new BigDecimal(rangeAndFee[1].trim()).setScale(2, RoundingMode.HALF_UP));
                })
                .sorted(Comparator.comparingDouble(Slab::fromKm))
                .toList();
    }

    /** How far (straight line) a store of this type may be from the delivery address to be shown and ordered from. */
    public int radiusMeters(MerchantType type) {
        return meters(type == MerchantType.KIRANA ? delivery.kiranaRadiusKm() : delivery.restaurantRadiusKm());
    }

    /** The cart's "add a meal" suggestions only come from restaurants this close (straight line). */
    public int mealNudgeRadiusMeters() {
        return meters(delivery.mealNudgeRadiusKm());
    }

    public double freeDeliveryKm() {
        return delivery.freeDeliveryKm();
    }

    /** Road distance from the store to the customer in km, one decimal: the number shown and the one the fee uses. */
    public double roadKm(GeoPoint store, GeoPoint customer) {
        return roadKm(routingClient.route(List.of(store, customer)).distanceMeters());
    }

    public static double roadKm(int meters) {
        return BigDecimal.valueOf(meters / 1000.0).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    public boolean insideFreeZone(double roadKm) {
        return roadKm <= delivery.freeDeliveryKm();
    }

    /**
     * The slab fee for an order whose farthest pickup is {@code roadKm} away; zero inside the free zone.
     * TODO(edge-case): stores are visible by straight line (6 km) but slabs end at 6 km by road, so a store
     * farther than the last slab by road pays the last slab's fee.
     */
    public BigDecimal slabFee(double roadKm) {
        if (insideFreeZone(roadKm) || slabs.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        }
        return slabs.stream().filter(slab -> roadKm <= slab.toKm()).findFirst().orElse(slabs.getLast()).fee();
    }

    private static int meters(double km) {
        return (int) Math.round(km * 1000);
    }
}
