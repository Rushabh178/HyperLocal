package com.HyperLocalSystem.app.cart.service;

import com.HyperLocalSystem.app.cart.dto.CartDtos.BillSummary;
import com.HyperLocalSystem.app.config.AppProperties;
import com.HyperLocalSystem.app.delivery.DeliveryPricing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Unit-economics rules, shared by the cart and checkout so both always agree:
 * one courier → at most one delivery fee (a distance slab beyond the free-delivery zone; inside it
 * waived when the cart has food or groceries reach the nudge threshold) plus one flat convenience fee;
 * GST on food only; no grocery minimum.
 */
@Component
@RequiredArgsConstructor
public class BillCalculator {

    private final AppProperties properties;
    private final DeliveryPricing pricing;

    /**
     * @param feeKm fee distance of the order's farthest pickup to the delivery address, or null when not known
     *              yet (no address chosen): then the order is priced as inside the free-delivery zone
     */
    public BillSummary bill(BigDecimal foodSubtotal, BigDecimal grocerySubtotal, Double feeKm) {
        AppProperties.Ops ops = properties.ops();
        BigDecimal food = money(foodSubtotal);
        BigDecimal grocery = money(grocerySubtotal);
        if (food.signum() == 0 && grocery.signum() == 0) {
            BigDecimal zero = money(BigDecimal.ZERO);
            return new BillSummary(zero, zero, zero, zero, zero, zero, zero, zero, zero);
        }
        BigDecimal deliveryFee = deliveryFee(food, grocery, feeKm);
        BigDecimal convenienceFee = ops.convenienceFee();
        BigDecimal taxes = food.multiply(ops.foodTaxRate());
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal total = food.add(grocery).add(deliveryFee).add(convenienceFee).add(taxes).subtract(discount);
        return new BillSummary(food, grocery, money(deliveryFee), money(convenienceFee), money(taxes),
                money(discount), money(total), money(BigDecimal.ZERO), money(total));
    }

    /**
     * The delivery fee (also used to work out what a refund of the groceries gives back). Beyond the
     * free-delivery distance: the slab for {@code feeKm}, no waiver. Inside it: free, except a grocery-only
     * cart under the nudge threshold pays {@code ops.deliveryFee} (the meal nudge waives it).
     */
    public BigDecimal deliveryFee(BigDecimal food, BigDecimal grocery, Double feeKm) {
        if (feeKm != null && !pricing.insideFreeZone(feeKm)) {
            return pricing.slabFee(feeKm);
        }
        return deliveryWaived(food, grocery) ? BigDecimal.ZERO : properties.ops().deliveryFee();
    }

    /** Same, from an order's stored fee distance in meters (null for orders placed before distances were recorded). */
    public BigDecimal deliveryFee(BigDecimal food, BigDecimal grocery, Integer feeMeters) {
        return deliveryFee(food, grocery, feeMeters == null ? null : DeliveryPricing.km(feeMeters));
    }

    public boolean deliveryWaived(BigDecimal food, BigDecimal grocery) {
        return food.signum() > 0 || grocery.compareTo(properties.ops().groceryCartNudgeThreshold()) >= 0;
    }

    public static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
