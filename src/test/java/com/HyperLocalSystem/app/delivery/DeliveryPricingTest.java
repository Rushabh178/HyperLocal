package com.HyperLocalSystem.app.delivery;

import com.HyperLocalSystem.app.config.AppProperties;
import com.HyperLocalSystem.app.merchant.entity.MerchantType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Fee slab selection: free up to 2 km by road, then 2-3:15, 3-4:25, 4-5:35, 5-6:45. */
class DeliveryPricingTest {

    private final DeliveryPricing pricing = new DeliveryPricing(new AppProperties(null, null, null, null, null, null, null,
            new AppProperties.Delivery(6, 2, 2, "2-3:15, 3-4:25, 4-5:35, 5-6:45", 2), null),
            mock(com.HyperLocalSystem.app.routing.RoutingClient.class));

    @Test
    void feeSlabSelection() {
        assertThat(pricing.slabFee(0.4)).isEqualByComparingTo("0");
        assertThat(pricing.slabFee(2.0)).isEqualByComparingTo("0");
        assertThat(pricing.slabFee(2.1)).isEqualByComparingTo("15");
        assertThat(pricing.slabFee(3.0)).isEqualByComparingTo("15");
        assertThat(pricing.slabFee(3.1)).isEqualByComparingTo("25");
        assertThat(pricing.slabFee(4.6)).isEqualByComparingTo("35");
        assertThat(pricing.slabFee(6.0)).isEqualByComparingTo("45");
        // Beyond the last slab by road (still visible by straight line): the last slab's fee.
        assertThat(pricing.slabFee(7.4)).isEqualByComparingTo("45");
        assertThat(pricing.slabFee(3.5).scale()).isEqualTo(2);

        assertThat(pricing.radiusMeters(MerchantType.RESTAURANT)).isEqualTo(6000);
        assertThat(pricing.radiusMeters(MerchantType.KIRANA)).isEqualTo(2000);
        assertThat(pricing.mealNudgeRadiusMeters()).isEqualTo(2000);
        assertThat(DeliveryPricing.parse("5-6:45,2-3:15").getFirst().fee()).isEqualTo(new BigDecimal("15.00"));
    }
}
