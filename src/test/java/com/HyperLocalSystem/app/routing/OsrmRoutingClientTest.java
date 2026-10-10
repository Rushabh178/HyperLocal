package com.HyperLocalSystem.app.routing;

import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** OSRM request format (lng,lat order) and response parsing, against a mock HTTP server. */
class OsrmRoutingClientTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OsrmRoutingClient client = new OsrmRoutingClient(builder,
            new AppProperties(null, null, null, new AppProperties.Routing(1.3, 20, "http://osrm.test"), null, null, null, null, null));

    private final GeoPoint a = new GeoPoint(18.52, 73.85);
    private final GeoPoint b = new GeoPoint(18.53, 73.86);

    @Test
    void routeSendsLngLatAndParsesDistanceAndDuration() {
        server.expect(requestTo("http://osrm.test/route/v1/driving/73.850000,18.520000;73.860000,18.530000?overview=full&geometries=polyline"))
                .andRespond(withSuccess("""
                        {"code":"Ok","routes":[{"distance":1534.6,"duration":301.2,"geometry":"abc"}]}""", MediaType.APPLICATION_JSON));

        RoutingClient.Route route = client.route(List.of(a, b));

        assertThat(route.distanceMeters()).isEqualTo(1535);
        assertThat(route.duration()).isEqualTo(Duration.ofSeconds(301));
        assertThat(route.encodedPolyline()).isEqualTo("abc");
        server.verify();
    }

    @Test
    void matrixSplitsSourcesAndDestinationsAndMarksUnreachable() {
        server.expect(requestTo("http://osrm.test/table/v1/driving/73.850000,18.520000;73.860000,18.530000;73.860000,18.530000"
                        + "?sources=0&destinations=1;2&annotations=duration"))
                .andRespond(withSuccess("""
                        {"code":"Ok","durations":[[120.4, null]]}""", MediaType.APPLICATION_JSON));

        long[][] seconds = client.durationMatrix(List.of(a), List.of(b, b));

        assertThat(seconds[0][0]).isEqualTo(120);
        assertThat(seconds[0][1]).isEqualTo(OsrmRoutingClient.UNREACHABLE_SECONDS);
        server.verify();
    }
}
