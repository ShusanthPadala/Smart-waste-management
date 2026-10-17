package com.example.smartwaste.algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure routing maths, free of Spring and JPA so it can be unit tested directly.
 *
 * <ul>
 *   <li>{@link #haversineKm} - great-circle distance between two coordinates.</li>
 *   <li>{@link #nearestNeighbourTour} - greedy construction heuristic starting at the depot.</li>
 *   <li>{@link #twoOpt} - local search that removes crossing edges until no improving swap is left.</li>
 *   <li>{@link #sweepClusters} - splits stops between vehicles by polar angle around the depot,
 *       respecting each vehicle's payload capacity (the classic Gillett-Miller sweep).</li>
 * </ul>
 */
public final class RoutingEngine {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private RoutingEngine() {}

    public record Point(double lat, double lon) {}

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static double distance(Point a, Point b) {
        return haversineKm(a.lat(), a.lon(), b.lat(), b.lon());
    }

    /** Order in which to visit {@code stops}, as indexes into that list. */
    public static List<Integer> nearestNeighbourTour(Point depot, List<Point> stops) {
        List<Integer> tour = new ArrayList<>(stops.size());
        boolean[] visited = new boolean[stops.size()];
        Point current = depot;
        for (int step = 0; step < stops.size(); step++) {
            int best = -1;
            double bestDist = Double.MAX_VALUE;
            for (int i = 0; i < stops.size(); i++) {
                if (visited[i]) continue;
                double d = distance(current, stops.get(i));
                if (d < bestDist) {
                    bestDist = d;
                    best = i;
                }
            }
            visited[best] = true;
            tour.add(best);
            current = stops.get(best);
        }
        return tour;
    }

    /** Improves a closed depot -> stops -> depot tour with 2-opt edge swaps. */
    public static List<Integer> twoOpt(Point depot, List<Point> stops, List<Integer> tour) {
        if (tour.size() < 3) return new ArrayList<>(tour);

        // Work on a node list where index 0 and the last index are the depot.
        List<Point> nodes = new ArrayList<>(tour.size() + 2);
        nodes.add(depot);
        for (int idx : tour) nodes.add(stops.get(idx));
        nodes.add(depot);
        List<Integer> order = new ArrayList<>(tour);

        boolean improved = true;
        int guard = 0;
        while (improved && guard++ < 1000) {
            improved = false;
            for (int i = 1; i < nodes.size() - 2; i++) {
                for (int k = i + 1; k < nodes.size() - 1; k++) {
                    double before = distance(nodes.get(i - 1), nodes.get(i)) + distance(nodes.get(k), nodes.get(k + 1));
                    double after = distance(nodes.get(i - 1), nodes.get(k)) + distance(nodes.get(i), nodes.get(k + 1));
                    if (after < before - 1e-9) {
                        reverse(nodes, i, k);
                        reverse(order, i - 1, k - 1);
                        improved = true;
                    }
                }
            }
        }
        return order;
    }

    /** Length of the closed tour depot -> stops in {@code tour} order -> depot. */
    public static double tourLengthKm(Point depot, List<Point> stops, List<Integer> tour) {
        double total = 0;
        Point current = depot;
        for (int idx : tour) {
            total += distance(current, stops.get(idx));
            current = stops.get(idx);
        }
        return total + distance(current, depot);
    }

    /** Convenience: nearest-neighbour followed by 2-opt. */
    public static List<Integer> optimisedTour(Point depot, List<Point> stops) {
        return twoOpt(depot, stops, nearestNeighbourTour(depot, stops));
    }

    /**
     * Assigns stops to vehicles by sweeping clockwise around the depot. A stop that does not fit in
     * the current vehicle moves the sweep on to the next vehicle; stops that fit nowhere are returned
     * in the last list element (the "unassigned" bucket).
     *
     * @return one index list per vehicle, plus a final list of unassigned stop indexes
     */
    public static List<List<Integer>> sweepClusters(Point depot, List<Point> stops, List<Double> loads, List<Double> capacities) {
        List<Integer> byAngle = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) byAngle.add(i);
        byAngle.sort(Comparator.comparingDouble(i -> Math.atan2(stops.get(i).lat() - depot.lat(), stops.get(i).lon() - depot.lon())));

        List<List<Integer>> clusters = new ArrayList<>();
        for (int v = 0; v < capacities.size(); v++) clusters.add(new ArrayList<>());
        List<Integer> unassigned = new ArrayList<>();

        int vehicle = 0;
        double used = 0;
        for (int idx : byAngle) {
            double load = loads.get(idx);
            while (vehicle < capacities.size() && used + load > capacities.get(vehicle)) {
                // Allow a near-empty vehicle to skip a single oversize bin rather than abandoning the sweep.
                if (used == 0) break;
                vehicle++;
                used = 0;
            }
            if (vehicle >= capacities.size() || load > capacities.get(vehicle)) {
                unassigned.add(idx);
                continue;
            }
            clusters.get(vehicle).add(idx);
            used += load;
        }
        clusters.add(unassigned);
        return clusters;
    }

    private static <T> void reverse(List<T> list, int from, int to) {
        while (from < to) {
            T tmp = list.get(from);
            list.set(from, list.get(to));
            list.set(to, tmp);
            from++;
            to--;
        }
    }
}
