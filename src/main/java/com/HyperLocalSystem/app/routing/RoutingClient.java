package com.HyperLocalSystem.app.routing;

import com.HyperLocalSystem.app.common.geo.GeoPoint;

import java.time.Duration;
import java.util.List;

/** Road-network routing (OSRM / GraphHopper). Straight-line distance is never good enough for ETAs. */
public interface RoutingClient {

    record Route(int distanceMeters, Duration duration, String encodedPolyline, List<GeoPoint> waypoints) {
    }

    /** Route through {@code stops} in the given order. */
    Route route(List<GeoPoint> stops);

    /** travel-time matrix: result[i][j] = seconds from sources[i] to destinations[j]. */
    long[][] durationMatrix(List<GeoPoint> sources, List<GeoPoint> destinations);
}
