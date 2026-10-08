package com.HyperLocalSystem.app.common.json;

import org.springframework.boot.jackson.JacksonComponent;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Every BigDecimal in the API is money in rupees, written as a JSON number with exactly two
 * decimals ({@code 25.00}), on REST, WebSocket and push payloads alike. The apps never compute
 * money; they show these values. (Non-money decimals such as ratings are Double.)
 */
@JacksonComponent
public class MoneySerializer extends ValueSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal value, JsonGenerator generator, SerializationContext context) {
        generator.writeNumber(value.setScale(2, RoundingMode.HALF_UP));
    }

    @Override
    public Class<BigDecimal> handledType() {
        return BigDecimal.class;
    }
}
