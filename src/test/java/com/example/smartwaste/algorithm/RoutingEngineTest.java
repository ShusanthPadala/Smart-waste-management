package com.example.smartwaste.algorithm;

import com.example.smartwaste.algorithm.RoutingEngine.Point;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RoutingEngineTest {

    private static final Point DEPOT = new Point(17.44, 78.35);

    @Test
    void haversineMatchesKnownDistance() {
        // New York -> Los Angeles is ~3936 km on the great circle.
        double km = RoutingEngine.haversineKm(40.7128, -74.0060, 34.0522, -118.2437);
        assertEquals(3936, km, 10);
        assertEquals(0, RoutingEngine.haversineKm(17.4, 78.3, 17.4, 78.3), 1e-9);
    }

    @Test
    void tourVisitsEveryStopExactlyOnce() {
        List<Point> stops = randomStops(15, 1);
        List<Integer> tour = RoutingEngine.optimisedTour(DEPOT, stops);
        assertEquals(15, tour.size());
        assertEquals(15, new HashSet<>(tour).size());
    }

    @Test
    void twoOptNeverMakesATourLonger() {
        for (int seed = 0; seed < 25; seed++) {
            List<Point> stops = randomStops(12, seed);
            List<Integer> nn = RoutingEngine.nearestNeighbourTour(DEPOT, stops);
            List<Integer> improved = RoutingEngine.twoOpt(DEPOT, stops, nn);
            assertTrue(RoutingEngine.tourLengthKm(DEPOT, stops, improved)
                    <= RoutingEngine.tourLengthKm(DEPOT, stops, nn) + 1e-9, "seed " + seed);
        }
    }

    @Test
    void twoOptUncrossesASquare() {
        // Corners of a square visited in a crossing order (0 -> 2 -> 1 -> 3) should be fixed.
        Point depot = new Point(0, 0);
        List<Point> stops = List.of(new Point(0, 0.01), new Point(0.01, 0.01), new Point(0.01, 0), new Point(0.005, -0.001));
        List<Integer> crossing = List.of(0, 2, 1, 3);
        List<Integer> fixed = RoutingEngine.twoOpt(depot, stops, crossing);
        assertTrue(RoutingEngine.tourLengthKm(depot, stops, fixed) < RoutingEngine.tourLengthKm(depot, stops, crossing));
    }

    @Test
    void sweepRespectsVehicleCapacity() {
        List<Point> stops = randomStops(10, 7);
        List<Double> loads = new ArrayList<>();
        for (int i = 0; i < 10; i++) loads.add(1000.0);
        List<Double> capacities = List.of(3000.0, 3000.0, 3000.0);

        List<List<Integer>> clusters = RoutingEngine.sweepClusters(DEPOT, stops, loads, capacities);

        assertEquals(4, clusters.size(), "3 vehicles + unassigned bucket");
        for (int v = 0; v < 3; v++) {
            double load = clusters.get(v).stream().mapToDouble(loads::get).sum();
            assertTrue(load <= capacities.get(v));
        }
        assertEquals(1, clusters.get(3).size(), "10 x 1000 L cannot fit in 9000 L");
    }

    @Test
    void oversizeStopIsLeftUnassigned() {
        List<Point> stops = List.of(new Point(17.45, 78.36), new Point(17.46, 78.37));
        List<List<Integer>> clusters = RoutingEngine.sweepClusters(DEPOT, stops, List.of(500.0, 9000.0), List.of(4000.0));
        assertEquals(List.of(0), clusters.get(0));
        assertEquals(List.of(1), clusters.get(1));
    }

    private static List<Point> randomStops(int n, long seed) {
        Random r = new Random(seed);
        List<Point> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new Point(17.40 + r.nextDouble() * 0.08, 78.32 + r.nextDouble() * 0.08));
        return out;
    }
}
