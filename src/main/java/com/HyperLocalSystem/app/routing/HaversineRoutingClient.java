package com.HyperLocalSystem.app.routing;

import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.common.geo.GeoUtils;
import com.HyperLocalSystem.app.config.AppProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Default RoutingClient until an OSRM server exists: straight-line distance x detour factor (1.3)
 * at a flat average speed (20 km/h). Good enough for ordering candidates and rough ETAs; switch to
 * the "osrm" profile for real road times.
 */
@Component
@Profile("!osrm")
public class HaversineRoutingClient implements RoutingClient {

    private final double detourFactor;
    private final double metersPerSecond;

    public HaversineRoutingClient(AppProperties properties) {
        this.detourFactor = properties.routing().detourFactor();
        this.metersPerSecond = properties.routing().averageSpeedKmph() * 1000 / 3600;
    }

    @Override
    public Route route(List<GeoPoint> stops) {
        double meters = 0;
        for (int i = 1; i < stops.size(); i++) {
            meters += roadMeters(stops.get(i - 1), stops.get(i));
        }
        return new Route((int) Math.round(meters), Duration.ofSeconds(Math.round(meters / metersPerSecond)), null, stops);
    }

    @Override
    public long[][] durationMatrix(List<GeoPoint> sources, List<GeoPoint> destinations) {
        long[][] seconds = new long[sources.size()][destinations.size()];
        for (int i = 0; i < sources.size(); i++) {
            for (int j = 0; j < destinations.size(); j++) {
                seconds[i][j] = Math.round(roadMeters(sources.get(i), destinations.get(j)) / metersPerSecond);
            }
        }
        return seconds;
    }

    private double roadMeters(GeoPoint a, GeoPoint b) {
        return GeoUtils.haversineMeters(a, b) * detourFactor;
    }
}
