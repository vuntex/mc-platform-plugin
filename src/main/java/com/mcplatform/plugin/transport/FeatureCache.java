package com.mcplatform.plugin.transport;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Generic, per-feature local cache with version-aware writes. The {@code version} is a monotonically
 * increasing sequence (e.g. the backend's {@code sequence_no}): a write only wins if it is strictly
 * newer than what is cached, so out-of-order live updates can never move a value backwards, and a
 * re-delivery of the same version is idempotent (the first write at that version stays).
 *
 * <p>Thread-safe: writes (bus thread) and reads (main thread) go through a {@link ConcurrentMap}, and
 * the compare-and-keep is atomic via {@link ConcurrentMap#merge}.
 *
 * @param <K> cache key (e.g. player+currency)
 * @param <V> cached value (e.g. a balance)
 */
public final class FeatureCache<K, V> {

    private record Versioned<T>(T value, long version) {
    }

    private final ConcurrentMap<K, Versioned<V>> entries = new ConcurrentHashMap<>();

    /** Insert/update {@code key} with {@code value} observed at {@code version}; older versions lose. */
    public void put(K key, V value, long version) {
        entries.merge(key, new Versioned<>(value, version),
                (existing, incoming) -> incoming.version() > existing.version() ? incoming : existing);
    }

    public Optional<V> get(K key) {
        Versioned<V> current = entries.get(key);
        return current == null ? Optional.empty() : Optional.of(current.value());
    }

    /** The version currently cached for {@code key}, if any. */
    public OptionalLong version(K key) {
        Versioned<V> current = entries.get(key);
        return current == null ? OptionalLong.empty() : OptionalLong.of(current.version());
    }

    public void remove(K key) {
        entries.remove(key);
    }

    public int size() {
        return entries.size();
    }
}
