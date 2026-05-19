package turoran.robrowserlegacy.service;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Singleton;
import turoran.robrowserlegacy.model.CacheEntry;
import turoran.robrowserlegacy.model.CacheStats;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class LRUCacheService {
    private static final int    DEFAULT_MAX_SIZE   = 5_000;
    private static final int    DEFAULT_MAX_MEMORY_MB = 1_024;

    private final int  maxSize;
    private final long maxMemoryBytes;

    /** Running total of bytes currently held in the cache. */
    private final AtomicLong currentMemory = new AtomicLong(0);

    private final AtomicLong hits   = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);

    /** Caffeine cache – eviction is weight-based (bytes) + size-based. */
    private final Cache<String, CacheEntry> cache;

    public LRUCacheService() {
        this(DEFAULT_MAX_SIZE, DEFAULT_MAX_MEMORY_MB);
    }

    public LRUCacheService(int maxSize, int maxMemoryMB) {
        this.maxSize = maxSize;
        this.maxMemoryBytes = (long) maxMemoryMB * 1024 * 1024;
        this.cache = Caffeine.newBuilder()
                // LRU eviction: each access refreshes the entry's position
                .maximumWeight(maxMemoryBytes)
                // Weight = number of bytes stored for that entry
                .<String, CacheEntry>weigher((key, entry) -> entry.size())
                // Also cap by entry count
                .maximumSize(maxSize)
                // Keep currentMemory in sync when Caffeine evicts entries
                .removalListener((key, entry, cause) -> {
                    if (entry != null) {
                        currentMemory.addAndGet(-entry.size());
                    }
                })
                .build();
    }

    /**
     * Retrieves an entry from the cache.
     *
     * @param key cache key
     * @return the {@link CacheEntry}, or {@code null} if not present
     */
    public CacheEntry get(String key) {
        CacheEntry entry = cache.getIfPresent(key);
        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return entry;
    }

    /**
     * Stores file bytes in the cache.
     *
     * <p>Silently does nothing when {@code data} is {@code null}, empty,
     * or larger than 10 % of the configured max memory.
     *
     * @param key  cache key
     * @param data raw file bytes
     */
    public void set(String key, byte[] data) {
        if (data == null || data.length == 0) return;

        int size = data.length;

        // Mirror JS: don't cache files > 10 % of max memory
        if (size > maxMemoryBytes * 0.1) return;

        // If the key already exists, remove it first to keep currentMemory accurate
        CacheEntry existing = cache.getIfPresent(key);
        if (existing != null) {
            cache.invalidate(key);
            // removalListener will subtract existing.size() from currentMemory
        }

        String etag = computeEtag(data);
        CacheEntry entry = new CacheEntry(data, etag, size);

        // Caffeine handles eviction of other entries automatically via weigher
        cache.put(key, entry);
        currentMemory.addAndGet(size);
    }

    /**
     * Returns {@code true} if the cache contains an entry for {@code key}.
     *
     * @param key cache key
     */
    public boolean has(String key) {
        return cache.getIfPresent(key) != null;
    }

    /**
     * Removes all entries from the cache.
     */
    public void clear() {
        cache.invalidateAll();
        currentMemory.set(0);
    }

    /**
     * Returns a snapshot of current cache statistics.
     *
     * @return {@link CacheStats}
     */
    public CacheStats getStats() {
        long h = hits.get();
        long m = misses.get();
        double hitRate = (h + m) > 0 ? (h * 100.0 / (h + m)) : 0.0;

        return new CacheStats(
                cache.estimatedSize(),
                maxSize,
                String.format("%.2f", currentMemory.get() / 1_048_576.0),
                String.format("%d",   maxMemoryBytes   / 1_048_576L),
                h,
                m,
                String.format("%.2f%%", hitRate)
        );
    }

    /**
     * Computes the first 16 hex characters of the MD5 digest of {@code data}.
     * Matches the ETag strategy used in the original JavaScript implementation.
     */
    private static String computeEtag(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            // MD5 is guaranteed to be present in every JVM
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }
}
