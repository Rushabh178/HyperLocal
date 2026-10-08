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
}
