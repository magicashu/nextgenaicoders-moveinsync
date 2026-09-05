package com.moveinsync.mobilitycopilot.reporting.application;

import java.util.LinkedHashMap;
import java.util.function.Supplier;

/** Bounded process-local snapshots. Fixed lock stripes prevent unbounded per-key lock retention. */
public final class SnapshotCache<K, V> {
    private final int capacity;
    private final LinkedHashMap<K, Entry<V>> values = new LinkedHashMap<>(16, .75f, true);
    private final Object[] locks = new Object[64];
    private record Entry<V>(V value) {}

    public SnapshotCache(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        for (int i = 0; i < locks.length; i++) locks[i] = new Object();
    }

    public V get(K key, boolean refresh, Supplier<V> loader) {
        Entry<V> observed;
        synchronized (values) { observed = values.get(key); }
        if (!refresh && observed != null) return observed.value();
        synchronized (locks[Math.floorMod(key.hashCode(), locks.length)]) {
            Entry<V> current;
            synchronized (values) { current = values.get(key); }
            // A request that waited for the same refresh reuses that completed capture.
            if (current != null && (!refresh || current != observed)) return current.value();
            V loaded = loader.get(); // Failure preserves the previous successful capture.
            synchronized (values) {
                values.put(key, new Entry<>(loaded));
                while (values.size() > capacity) values.remove(values.keySet().iterator().next());
            }
            return loaded;
        }
    }
}
