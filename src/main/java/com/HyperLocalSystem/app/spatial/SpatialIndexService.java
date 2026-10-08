package com.HyperLocalSystem.app.spatial;

import com.HyperLocalSystem.app.config.AppProperties;
import com.uber.h3core.H3Core;
import com.uber.h3core.LengthUnit;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Uber H3 hexagonal partitioning. Every agent, address and merchant stores its cell so
 * "who is near X" becomes an indexed {@code h3_index IN (...)} lookup instead of a full scan.
 */
@Service
public class SpatialIndexService {

    private final H3Core h3;
    private final int resolution;
    /** Smallest centre-to-centre step between neighbouring cells, with margin for H3's size variation. */
    private final double ringStepMeters;

    public SpatialIndexService(AppProperties properties) {
        try {
            this.h3 = H3Core.newInstance();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load the H3 native library", e);
        }
        this.resolution = properties.ops().h3Resolution();
        this.ringStepMeters = h3.getHexagonEdgeLengthAvg(resolution, LengthUnit.m) * 1.3;
    }

    public String cellOf(double lat, double lng) {
        return h3.latLngToCellAddress(lat, lng, resolution);
    }

    /**
     * The cell plus every cell within {@code k} rings of it. Used for the Kirana broadcast's
     * "concentric ring expansion" when no shop claims an order within the timeout.
     */
    public List<String> disk(String cell, int k) {
        return h3.gridDisk(cell, k);
    }

    /** Ring count whose disk around a point covers at least {@code radiusMeters} in every direction. */
    public int ringsFor(int radiusMeters) {
        return (int) Math.ceil(radiusMeters / ringStepMeters) + 1;
    }

    /**
     * Cells that cover a circle of {@code radiusMeters} around the point: the coarse pre-filter for
     * every nearby search. Callers still apply an exact ST_DWithin on the rows it returns.
     */
    public List<String> cellsWithin(double lat, double lng, int radiusMeters) {
        return disk(cellOf(lat, lng), ringsFor(radiusMeters));
    }
}
