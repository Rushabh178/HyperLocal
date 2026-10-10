package com.HyperLocalSystem.app.delivery;

import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.config.AppProperties;
import com.HyperLocalSystem.app.config.AppProperties.Delivery.FeeDistanceBasis;
import com.HyperLocalSystem.app.merchant.entity.MerchantType;
import com.HyperLocalSystem.app.routing.RoutingClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Fee slabs (free up to 2 km, then 2-3:15, 3-4:25, 4-5:35, 5-6:45) and the two fee-distance bases. */
class DeliveryPricingTest {

    /** 4 km due east of the customer at Pune's latitude. */
    private static final GeoPoint CUSTOMER = new GeoPoint(18.5204, 73.8567);
    private static final GeoPoint STORE = new GeoPoint(18.5204, 73.8567 + 4000 / (111_320.0 * Math.cos(Math.toRadians(18.5204))));

    private final RoutingClient routing = mock(RoutingClient.class);

    private DeliveryPricing pricing(FeeDistanceBasis basis) {
        return new DeliveryPricing(new AppProperties(null, null, null, null, null, null, null,
                new AppProperties.Delivery(6, 2, 2, "2-3:15, 3-4:25, 4-5:35, 5-6:45", 2, basis), null), routing);
    }

    @Test
    void feeSlabSelection() {
        DeliveryPricing pricing = pricing(null);
        assertThat(pricing.slabFee(0.4)).isEqualByComparingTo("0");
        assertThat(pricing.slabFee(2.0)).isEqualByComparingTo("0");
        assertThat(pricing.slabFee(2.1)).isEqualByComparingTo("15");
        assertThat(pricing.slabFee(3.0)).isEqualByComparingTo("15");
        assertThat(pricing.slabFee(3.1)).isEqualByComparingTo("25");
        assertThat(pricing.slabFee(4.6)).isEqualByComparingTo("35");
        assertThat(pricing.slabFee(6.0)).isEqualByComparingTo("45");
        // Beyond the last slab (possible on the road basis only): the last slab's fee.
        assertThat(pricing.slabFee(7.4)).isEqualByComparingTo("45");
        assertThat(pricing.slabFee(3.5).scale()).isEqualTo(2);

        assertThat(pricing.radiusMeters(MerchantType.RESTAURANT)).isEqualTo(6000);
        assertThat(pricing.radiusMeters(MerchantType.KIRANA)).isEqualTo(2000);
        assertThat(pricing.mealNudgeRadiusMeters()).isEqualTo(2000);
        assertThat(DeliveryPricing.parse("5-6:45,2-3:15").getFirst().fee()).isEqualTo(new BigDecimal("15.00"));
    }

    @Test
    void straightLineBasisIsTheDefaultAndNeverAsksTheRouter() {
        DeliveryPricing pricing = pricing(null);
        assertThat(pricing.basis()).isEqualTo(FeeDistanceBasis.STRAIGHT_LINE);
        assertThat(pricing.feeKm(STORE, CUSTOMER)).isEqualTo(4.0);
        assertThat(pricing.slabFee(pricing.feeKm(STORE, CUSTOMER))).isEqualByComparingTo("25");
        assertThat(pricing(FeeDistanceBasis.STRAIGHT_LINE).feeKm(List.of(STORE, CUSTOMER), CUSTOMER)).containsExactly(4.0, 0.0);
        verifyNoInteractions(routing);
    }

    @Test
    void roadBasisUsesTheRoutersDistance() {
        when(routing.distanceMatrix(anyList(), any())).thenReturn(new int[][]{{5240}});
        DeliveryPricing pricing = pricing(FeeDistanceBasis.ROAD);
        assertThat(pricing.feeKm(STORE, CUSTOMER)).isEqualTo(5.2);
        assertThat(pricing.slabFee(pricing.feeKm(STORE, CUSTOMER))).isEqualByComparingTo("45");
        // The free zone is judged on the same number: 1.7 km straight can be 2.2 km by road.
        when(routing.distanceMatrix(anyList(), any())).thenReturn(new int[][]{{2210}});
        assertThat(pricing.insideFreeZone(pricing.feeKm(STORE, CUSTOMER))).isFalse();
    }
}
