package com.HyperLocalSystem.app.routing;

import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.config.AppProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Road-network routing against a self-hosted OSRM server ("osrm" profile, {@code OSRM_BASE_URL}).
 * <ul>
 *   <li>route: GET /route/v1/driving/{lng,lat;lng,lat;...}?overview=full&amp;geometries=polyline</li>
 *   <li>matrix: GET /table/v1/driving/{coords}?sources=..&amp;destinations=..&amp;annotations=duration</li>
 * </ul>
 * OSRM takes coordinates as lng,lat. Unreachable pairs come back as a very large duration.
 * TODO(edge-case): timeouts, retries and a circuit breaker that falls back to HaversineRoutingClient.
 */
@Component
@Profile("osrm")
public class OsrmRoutingClient implements RoutingClient {

    /** Large enough to never win, small enough that summing a few never overflows. */
    static final long UNREACHABLE_SECONDS = Long.MAX_VALUE / 1024;

    private final RestClient client;

    public OsrmRoutingClient(RestClient.Builder builder, AppProperties properties) {
        this.client = builder.baseUrl(properties.routing().osrmBaseUrl()).build();
    }

    @Override
    public Route route(List<GeoPoint> stops) {
        if (stops.size() < 2) {
            return new Route(0, Duration.ZERO, null, stops);
        }
        JsonNode body = client.get()
                .uri("/route/v1/driving/" + coordinates(stops) + "?overview=full&geometries=polyline")
                .retrieve().body(JsonNode.class);
        JsonNode route = body.path("routes").path(0);
        if (!"Ok".equals(body.path("code").asString()) || route.isMissingNode()) {
            throw new IllegalStateException("OSRM could not route: " + body.path("code").asString());
        }
        return new Route((int) Math.round(route.path("distance").asDouble()),
                Duration.ofSeconds(Math.round(route.path("duration").asDouble())),
                route.path("geometry").asString(null), stops);
    }

    @Override
    public long[][] durationMatrix(List<GeoPoint> sources, List<GeoPoint> destinations) {
        JsonNode durations = table(sources, destinations, "duration").path("durations");
        long[][] seconds = new long[sources.size()][destinations.size()];
        for (int i = 0; i < sources.size(); i++) {
            for (int j = 0; j < destinations.size(); j++) {
                JsonNode cell = durations.path(i).path(j);
                seconds[i][j] = cell.isNumber() ? Math.round(cell.asDouble()) : UNREACHABLE_SECONDS;
            }
        }
        return seconds;
    }

    @Override
    public int[][] distanceMatrix(List<GeoPoint> sources, List<GeoPoint> destinations) {
        JsonNode distances = table(sources, destinations, "distance").path("distances");
        int[][] meters = new int[sources.size()][destinations.size()];
        for (int i = 0; i < sources.size(); i++) {
            for (int j = 0; j < destinations.size(); j++) {
                JsonNode cell = distances.path(i).path(j);
                meters[i][j] = cell.isNumber() ? (int) Math.round(cell.asDouble()) : Integer.MAX_VALUE / 1024;
            }
        }
        return meters;
    }

    /** One OSRM /table call; {@code annotation} is "duration" or "distance". */
    private JsonNode table(List<GeoPoint> sources, List<GeoPoint> destinations, String annotation) {
        List<GeoPoint> all = new ArrayList<>(sources);
        all.addAll(destinations);
        String sourceIndexes = indexes(0, sources.size());
        String destinationIndexes = indexes(sources.size(), all.size());
        JsonNode body = client.get()
                .uri("/table/v1/driving/" + coordinates(all) + "?sources=" + sourceIndexes
                        + "&destinations=" + destinationIndexes + "&annotations=" + annotation)
                .retrieve().body(JsonNode.class);
        if (!"Ok".equals(body.path("code").asString())) {
            throw new IllegalStateException("OSRM table failed: " + body.path("code").asString());
        }
        return body;
    }

    private static String coordinates(List<GeoPoint> points) {
        return points.stream()
                .map(p -> String.format(Locale.ROOT, "%.6f,%.6f", p.lng(), p.lat()))
                .collect(Collectors.joining(";"));
    }

    private static String indexes(int fromInclusive, int toExclusive) {
        return IntStream.range(fromInclusive, toExclusive).mapToObj(Integer::toString).collect(Collectors.joining(";"));
    }
}
