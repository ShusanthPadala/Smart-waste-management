package com.example.smartwaste.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A clock that can run faster than real time (or be paused) so that a day of bin usage
 * can be demonstrated in minutes. Every time-dependent service reads "now" from this clock.
 */
@Component
public class SimulationClock extends Clock {

    private final ZoneId zone = ZoneId.systemDefault();
    private Instant anchorSim = Instant.now();
    private long anchorRealNanos = System.nanoTime();
    private double speed;
    private boolean paused;

    public SimulationClock(@Value("${smartwaste.simulation.speed:60}") double speed) {
        this.speed = Math.max(1.0, speed);
    }

    @Override
    public synchronized Instant instant() {
        if (paused) return anchorSim;
        long elapsedNanos = System.nanoTime() - anchorRealNanos;
        return anchorSim.plusNanos((long) (elapsedNanos * speed));
    }

    public synchronized void setSpeed(double newSpeed) {
        reanchor();
        this.speed = Math.max(1.0, newSpeed);
    }

    public synchronized void setPaused(boolean paused) {
        reanchor();
        this.paused = paused;
    }

    /** Snap simulated time back to the real wall clock (used when demo data is reset). */
    public synchronized void resetToRealTime() {
        anchorSim = Instant.now();
        anchorRealNanos = System.nanoTime();
    }

    public synchronized double getSpeed() { return speed; }

    public synchronized boolean isPaused() { return paused; }

    private void reanchor() {
        anchorSim = instant();
        anchorRealNanos = System.nanoTime();
    }

    @Override
    public ZoneId getZone() { return zone; }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant(), zone);
    }
}
