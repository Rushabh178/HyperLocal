package com.HyperLocalSystem.app.cart.service;

import com.HyperLocalSystem.app.cart.dto.CartDtos.BillSummary;
import com.HyperLocalSystem.app.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Unit-economics rules, shared by the cart and checkout so both always agree:
 * one courier → at most one delivery fee (waived when the cart has food or groceries reach the
 * nudge threshold) plus one flat convenience fee; GST on food only; no grocery minimum.
 */
@Component
@RequiredArgsConstructor
public class BillCalculator {

    private final AppProperties properties;

    public BillSummary bill(BigDecimal foodSubtotal, BigDecimal grocerySubtotal) {
        AppProperties.Ops ops = properties.ops();
        BigDecimal food = money(foodSubtotal);
        BigDecimal grocery = money(grocerySubtotal);
        if (food.signum() == 0 && grocery.signum() == 0) {
            BigDecimal zero = money(BigDecimal.ZERO);
            return new BillSummary(zero, zero, zero, zero, zero, zero, zero);
        }
        BigDecimal deliveryFee = deliveryWaived(food, grocery) ? BigDecimal.ZERO : ops.deliveryFee();
        BigDecimal convenienceFee = ops.convenienceFee();
        BigDecimal taxes = food.multiply(ops.foodTaxRate());
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal total = food.add(grocery).add(deliveryFee).add(convenienceFee).add(taxes).subtract(discount);
        return new BillSummary(food, grocery, money(deliveryFee), money(convenienceFee), money(taxes),
                money(discount), money(total));
    }

    public boolean deliveryWaived(BigDecimal food, BigDecimal grocery) {
        return food.signum() > 0 || grocery.compareTo(properties.ops().groceryCartNudgeThreshold()) >= 0;
    }

    public static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
