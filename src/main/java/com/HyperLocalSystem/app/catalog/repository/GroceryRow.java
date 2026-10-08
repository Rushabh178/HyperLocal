package com.HyperLocalSystem.app.catalog.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** One de-duplicated product of the virtual warehouse (cheapest listing among nearby Kiranas). */
public interface GroceryRow {

    UUID getId();

    String getName();

    String getBrand();

    String getPackSize();

    BigDecimal getMrp();

    BigDecimal getPrice();

    String getCategory();

    String getZone();

    String getImageUrl();
}
