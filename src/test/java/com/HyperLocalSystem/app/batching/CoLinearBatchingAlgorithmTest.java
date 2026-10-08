package com.HyperLocalSystem.app.batching;

import com.HyperLocalSystem.app.batching.CoLinearBatchingAlgorithm.Plan;
import com.HyperLocalSystem.app.batching.CoLinearBatchingAlgorithm.Point;
import com.HyperLocalSystem.app.batching.CoLinearBatchingAlgorithm.TravelTime;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests of the batching maths: no Spring, no database. */
class CoLinearBatchingAlgorithmTest {

    private static final double LAT = 18.52;
    private static final double LNG = 73.85;

    /** Straight-line metres at 5 m/s (18 km/h). */
    private static final TravelTime STRAIGHT_LINE = (a, b) -> Math.round(meters(a, b) / 5.0);

    private final CoLinearBatchingAlgorithm algorithm = new CoLinearBatchingAlgorithm(500, 240);

    /** Rider heading north: restaurant 500 m ahead, customer 2 km ahead. */
    private final Point rider = at(0, 0);
    private final List<Point> route = List.of(at(500, 0), at(2000, 0));

    @Test
    void injectsStopsThatLieAlongTheWay() {
        Optional<Plan> plan = algorithm.plan(rider, route, at(800, 60), at(1500, 40), STRAIGHT_LINE);

        assertThat(plan).isPresent();
        // After the restaurant, pick up; drop before the original customer.
        assertThat(plan.get().pickupAfter()).isEqualTo(1);
        assertThat(plan.get().dropAfter()).isEqualTo(1);
        assertThat(plan.get().addedSeconds()).isBetween(0L, 60L);
    }

    @Test
    void rejectsStopsOutsideTheCorridor() {
        assertThat(algorithm.plan(rider, route, at(800, 900), at(1500, 40), STRAIGHT_LINE)).isEmpty();
        assertThat(algorithm.plan(rider, route, at(800, 60), at(1500, -700), STRAIGHT_LINE)).isEmpty();
    }

    @Test
    void rejectsInsertionsThatAddMoreThanTheLimit() {
        // Both inside the 500 m corridor but on opposite sides, 480 m off: the cheapest insertion adds ~233 s.
        Point pickup = at(600, 480);
        Point drop = at(700, -480);
        assertThat(new CoLinearBatchingAlgorithm(500, 120).plan(rider, route, pickup, drop, STRAIGHT_LINE)).isEmpty();
        Plan plan = algorithm.plan(rider, route, pickup, drop, STRAIGHT_LINE).orElseThrow();
        assertThat(plan.addedSeconds()).isBetween(200L, 240L);
    }

    @Test
    void dropNeverComesBeforePickup() {
        // Drop is near the start, pickup near the end: the drop must still follow the pickup.
        Plan plan = new CoLinearBatchingAlgorithm(500, 3600)
                .plan(rider, route, at(1900, 20), at(100, 20), STRAIGHT_LINE).orElseThrow();
        assertThat(plan.dropAfter()).isGreaterThanOrEqualTo(plan.pickupAfter());
    }

    private static Point at(double northMeters, double eastMeters) {
        return new Point(LAT + northMeters / 111_320.0, LNG + eastMeters / (111_320.0 * Math.cos(Math.toRadians(LAT))));
    }

    private static double meters(Point a, Point b) {
        double cos = Math.cos(Math.toRadians(LAT));
        return Math.hypot((a.lat() - b.lat()) * 111_320.0, (a.lng() - b.lng()) * 111_320.0 * cos);
    }
}
