package com.HyperLocalSystem.app.common.model;

/** Which chamber of the dual-compartment thermal box an item travels in. */
public enum TemperatureZone {
    /** Hot chamber (70% volume, above 65°C): cooked food. */
    HOT,
    /** Cold chamber (30% volume, 2-4°C): dairy, soft drinks, confectionery. */
    COLD,
    /** No temperature control needed: chips, packaged staples. Rides in the cold chamber if space allows. */
    AMBIENT
}
