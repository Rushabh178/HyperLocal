package com.HyperLocalSystem.app.batching;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Pathfinder's maths, with no Spring or database so it can be unit-tested and tuned in isolation.
 * <ol>
 *   <li><b>Corridor</b>: the new pickup and drop must both lie within {@code corridorMeters} of the
 *       rider's remaining path (current position → each unfinished stop, in locked order).</li>
 *   <li><b>Insertion</b>: try every place to put the pickup and, after it, the drop, without
 *       reordering the existing stops.</li>
 *   <li><b>SLA</b>: keep insertions that add at most {@code maxAddedSeconds} to the whole route
 *       (so no existing customer waits longer than that), and return the cheapest.</li>
 * </ol>
 */
public final class CoLinearBatchingAlgorithm {

    public record Point(double lat, double lng) {
    }

    /** Travel time between two points in seconds (road-network, haversine, or a precomputed matrix). */
    @FunctionalInterface
    public interface TravelTime {
        long seconds(Point from, Point to);
    }

    /**
     * Where to put the new stops: the pickup goes after the first {@code pickupAfter} existing stops,
     * the drop after the first {@code dropAfter} (always {@code >= pickupAfter}).
     */
    public record Plan(int pickupAfter, int dropAfter, long addedSeconds) {
    }

    private static final double METERS_PER_DEGREE = 111_320.0;

    private final double corridorMeters;
    private final long maxAddedSeconds;

    public CoLinearBatchingAlgorithm(double corridorMeters, long maxAddedSeconds) {
        this.corridorMeters = corridorMeters;
        this.maxAddedSeconds = maxAddedSeconds;
    }

    /**
     * @param rider     the rider's current position
     * @param remaining the rider's unfinished stops, in their locked order (not empty)
     */
    public Optional<Plan> plan(Point rider, List<Point> remaining, Point pickup, Point drop, TravelTime travel) {
        List<Point> path = new ArrayList<>(remaining.size() + 1);
        path.add(rider);
        path.addAll(remaining);
        if (distanceToPath(path, pickup) > corridorMeters || distanceToPath(path, drop) > corridorMeters) {
            return Optional.empty();
        }
        long baseline = duration(path, travel);
        Plan best = null;
        int n = remaining.size();
        for (int i = 0; i <= n; i++) {
            for (int j = i; j <= n; j++) {
                List<Point> candidate = new ArrayList<>(n + 3);
                candidate.add(rider);
                candidate.addAll(remaining.subList(0, i));
                candidate.add(pickup);
                candidate.addAll(remaining.subList(i, j));
                candidate.add(drop);
                candidate.addAll(remaining.subList(j, n));
                long added = duration(candidate, travel) - baseline;
                if (added <= maxAddedSeconds && (best == null || added < best.addedSeconds())) {
                    best = new Plan(i, j, added);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static long duration(List<Point> path, TravelTime travel) {
        long total = 0;
        for (int k = 1; k < path.size(); k++) {
            total += travel.seconds(path.get(k - 1), path.get(k));
        }
        return total;
    }

    /** Shortest distance in metres from {@code p} to the polyline (flat-earth, fine at city scale). */
    static double distanceToPath(List<Point> path, Point p) {
        if (path.size() == 1) {
            return planarDistance(path.getFirst(), p, p.lat());
        }
        double best = Double.MAX_VALUE;
        for (int k = 1; k < path.size(); k++) {
            best = Math.min(best, distanceToSegment(path.get(k - 1), path.get(k), p));
        }
        return best;
    }

    private static double distanceToSegment(Point a, Point b, Point p) {
        double cos = Math.cos(Math.toRadians(p.lat()));
        double ax = a.lng() * cos * METERS_PER_DEGREE, ay = a.lat() * METERS_PER_DEGREE;
        double bx = b.lng() * cos * METERS_PER_DEGREE, by = b.lat() * METERS_PER_DEGREE;
        double px = p.lng() * cos * METERS_PER_DEGREE, py = p.lat() * METERS_PER_DEGREE;
        double dx = bx - ax, dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lengthSquared));
        double cx = ax + t * dx, cy = ay + t * dy;
        return Math.hypot(px - cx, py - cy);
    }

    private static double planarDistance(Point a, Point b, double atLat) {
        double cos = Math.cos(Math.toRadians(atLat));
        return Math.hypot((a.lng() - b.lng()) * cos * METERS_PER_DEGREE, (a.lat() - b.lat()) * METERS_PER_DEGREE);
    }
}
