package com.HyperLocalSystem.app.common.geo;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/** Converts between API coordinates and PostGIS {@code geometry(Point, 4326)} values. */
public final class GeoUtils {

    public static final int WGS84_SRID = 4326;
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), WGS84_SRID);

    private GeoUtils() {
    }

    /** PostGIS stores points as (x = longitude, y = latitude). */
    public static Point toPoint(double lat, double lng) {
        return FACTORY.createPoint(new Coordinate(lng, lat));
    }

    public static Point toPoint(GeoPoint point) {
        return toPoint(point.lat(), point.lng());
    }

    public static GeoPoint fromPoint(Point point) {
        return point == null ? null : new GeoPoint(point.getY(), point.getX());
    }

    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    /** Great-circle distance in metres. */
    public static double haversineMeters(GeoPoint a, GeoPoint b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat())) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(h)));
    }
}
