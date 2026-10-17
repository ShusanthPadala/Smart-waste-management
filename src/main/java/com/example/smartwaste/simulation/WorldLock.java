package com.example.smartwaste.simulation;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Serialises changes to the simulated world. The simulation tick and user actions (emptying bins,
 * editing a bin, planning routes) both read-modify-write bins; without this, a tick that loaded a bin
 * just before a user emptied it would write the old fill level straight back.
 */
@Component
public class WorldLock {

    private final ReentrantLock lock = new ReentrantLock(true);

    public <T> T call(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public void run(Runnable action) {
        call(() -> {
            action.run();
            return null;
        });
    }
}
