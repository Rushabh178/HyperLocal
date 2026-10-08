package com.HyperLocalSystem.app.routing;

import com.HyperLocalSystem.app.common.exception.NotImplementedException;
import com.HyperLocalSystem.app.common.geo.GeoPoint;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TODO: call a self-hosted OSRM server (add app.routing.osrm-base-url):
 * <ul>
 *   <li>route: GET /route/v1/driving/{lng,lat;lng,lat;...}?overview=full&amp;geometries=polyline</li>
 *   <li>matrix: GET /table/v1/driving/{coords}?sources=..&amp;destinations=..</li>
 * </ul>
 * Note OSRM takes coordinates as lng,lat.
 */
@Component
public class OsrmRoutingClient implements RoutingClient {

    @Override
    public Route route(List<GeoPoint> stops) {
        throw new NotImplementedException("OSRM route");
    }

    @Override
    public long[][] durationMatrix(List<GeoPoint> sources, List<GeoPoint> destinations) {
        throw new NotImplementedException("OSRM duration matrix");
    }
}
