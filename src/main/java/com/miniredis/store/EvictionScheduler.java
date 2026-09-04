package com.miniredis.store;

import com.miniredis.model.DataValue;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EvictionScheduler {

    private static final int SWEEP_INTERVAL_MS = 100;
    private static final int MAX_KEYS_PER_SWEEP = 100;

    private final KeyValueStore store;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public EvictionScheduler(KeyValueStore store) {
        this.store = store;
    }

    /**
     * Starts the recurring background sweep.
     * Runs every SWEEP_INTERVAL_MS, checking up to MAX_KEYS_PER_SWEEP entries.
     */
    public void start() {
        scheduler.scheduleAtFixedRate(
                this::sweep,
                SWEEP_INTERVAL_MS,      // initial delay
                SWEEP_INTERVAL_MS,      // period
                TimeUnit.MILLISECONDS
        );
    }

    private void sweep() {
        ConcurrentHashMap<String, DataValue> map = store.getStoreView();
        Iterator<Map.Entry<String, DataValue>> iterator = map.entrySet().iterator();

        int checked = 0;
        int evicted = 0;

        while (iterator.hasNext() && checked < MAX_KEYS_PER_SWEEP) {
            Map.Entry<String, DataValue> entry = iterator.next();
            checked++;

            if (entry.getValue().isExpired()) {
                // Atomic remove-if-still-expired, same pattern as lazy GET eviction.
                // Prevents racing with a concurrent SET on this exact key.
                boolean removed = map.computeIfPresent(entry.getKey(), (k, dv) ->
                        dv.isExpired() ? null : dv
                ) == null;

                if (removed) {
                    evicted++;
                }
            }
        }

        if (evicted > 0) {
            System.out.printf("[EvictionScheduler] Swept %d keys, evicted %d expired keys.%n", checked, evicted);
        }
    }

    /**
     * Gracefully stops the background sweeper.
     * Should be called on server shutdown.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("[EvictionScheduler] Shut down cleanly.");
    }
}
