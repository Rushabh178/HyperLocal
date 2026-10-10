package com.HyperLocalSystem.app.delivery;

import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.common.geo.GeoUtils;
import com.HyperLocalSystem.app.config.AppProperties;
import com.HyperLocalSystem.app.config.AppProperties.Delivery.FeeDistanceBasis;
import com.HyperLocalSystem.app.merchant.entity.MerchantType;
import com.HyperLocalSystem.app.routing.RoutingClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Delivery range and distance pricing ({@code app.delivery.*}), all measured from the delivery address
 * to the store:
 * <ul>
 *   <li><b>Visibility</b> (which stores a customer can see / order from) is always the straight line:
 *       {@link #radiusMeters} (restaurants 6 km, Kiranas 2 km).</li>
 *   <li><b>The fee distance</b> ({@link #feeKm}) follows {@code fee-distance-basis}: the straight line
 *       (default) or the road distance from {@link RoutingClient}. The same number is shown as
 *       {@code distanceKm}, decides the free zone ({@code free-delivery-km}) and picks the slab.</li>
 * </ul>
 */
@Component
public class DeliveryPricing {

    /** One fee band: more than {@code fromKm} and at most {@code toKm} costs {@code fee}. */
    public record Slab(double fromKm, double toKm, BigDecimal fee) {
    }

    private final AppProperties.Delivery delivery;
    private final RoutingClient routingClient;
    private final FeeDistanceBasis basis;
    private final List<Slab> slabs;

    public DeliveryPricing(AppProperties properties, RoutingClient routingClient) {
        this.delivery = properties.delivery();
        this.routingClient = routingClient;
        this.basis = delivery.feeDistanceBasis() == null ? FeeDistanceBasis.STRAIGHT_LINE : delivery.feeDistanceBasis();
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

    public FeeDistanceBasis basis() {
        return basis;
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

    /** The fee distance from the store to the customer in km, one decimal: the number shown and the one the fee uses. */
    public double feeKm(GeoPoint store, GeoPoint customer) {
        return feeKm(List.of(store), customer)[0];
    }

    /** {@link #feeKm(GeoPoint, GeoPoint)} for many stores at once (one routing matrix call on the road basis). */
    public double[] feeKm(List<GeoPoint> stores, GeoPoint customer) {
        double[] km = new double[stores.size()];
        int[][] road = basis == FeeDistanceBasis.ROAD && !stores.isEmpty()
                ? routingClient.distanceMatrix(stores, List.of(customer)) : null;
        for (int i = 0; i < stores.size(); i++) {
            km[i] = km(road != null ? road[i][0] : (int) Math.round(GeoUtils.haversineMeters(stores.get(i), customer)));
        }
        return km;
    }

    /** Meters as km with one decimal, e.g. 1.4. */
    public static double km(int meters) {
        return BigDecimal.valueOf(meters / 1000.0).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    public boolean insideFreeZone(double feeKm) {
        return feeKm <= delivery.freeDeliveryKm();
    }

    /**
     * The slab fee for an order whose farthest pickup is {@code feeKm} away; zero inside the free zone.
     * Beyond the last slab the last slab's fee applies. On the straight-line basis that can't happen (stores
     * are only visible within the last slab's distance); on the road basis a store within 6 km in a straight
     * line can be farther by road.
     * TODO(edge-case): road basis only: decide whether stores beyond the last slab by road should be hidden or cost more.
     */
    public BigDecimal slabFee(double feeKm) {
        if (insideFreeZone(feeKm) || slabs.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        }
        return slabs.stream().filter(slab -> feeKm <= slab.toKm()).findFirst().orElse(slabs.getLast()).fee();
    }

    private static int meters(double km) {
        return (int) Math.round(km * 1000);
    }
}
