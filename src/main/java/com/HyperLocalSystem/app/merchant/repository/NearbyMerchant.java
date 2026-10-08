package com.HyperLocalSystem.app.merchant.repository;

import java.util.UUID;

/** Row of a nearby-merchant search: the store plus its exact distance from the searched point. */
public interface NearbyMerchant {

    UUID getId();

    String getName();

    String getCuisines();

    int getRatingSum();

    int getRatingCount();

    boolean getOpen();

    String getImageUrl();

    double getDistanceMeters();

    double getLat();

    double getLng();
}
