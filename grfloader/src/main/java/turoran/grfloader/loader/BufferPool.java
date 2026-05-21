package turoran.grfloader.loader;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple buffer pool for reducing GC pressure
 * Pools buffers of common sizes for reuse
 * <a href="https://github.com/FranciscoWallison/grf-loader/blob/main/src/buffer-pool.ts">BufferPool JS</a>
 */
public class BufferPool {

    private static class PoolEntry {
        final ByteBuffer buffer;
        boolean inUse;

        PoolEntry(ByteBuffer buffer) {
            this.buffer = buffer;
            this.inUse = false;
        }
    }

    private final Map<Integer, List<PoolEntry>> pools = new HashMap<>();

    // Common buffer sizes to pool (in bytes)
    private final int[] poolSizes = {
        1024,      // 1KB
        4096,      // 4KB
        8192,      // 8KB
        16384,     // 16KB
        32768,     // 32KB
        65536,     // 64KB
        131072,    // 128KB
        262144,    // 256KB
    };

    public BufferPool() {
        // Initialize pools for common sizes
        for (int size : poolSizes) {
            pools.put(size, new ArrayList<>());
        }
    }

    /**
     * Get appropriate pool size for requested length
     */
    private Integer getPoolSize(int length) {
        for (int size : poolSizes) {
            if (length <= size) {
                return size;
            }
        }
        return null; // Too large, don't pool
    }

    /**
     * Acquire a buffer from the pool or create new one
     */
    public ByteBuffer acquire(int length) {
        Integer poolSize = getPoolSize(length);

        // Don't pool large buffers
        if (poolSize == null) {
            return ByteBuffer.allocate(length);
        }

        List<PoolEntry> pool = pools.get(poolSize);

        if (pool != null) {
            // Try to find available buffer
            for (PoolEntry entry : pool) {
                if (!entry.inUse) {
                    entry.inUse = true;
                    entry.buffer.clear();
                    return (ByteBuffer) entry.buffer.limit(length);
                }
            }

            // Pool is full or all in use, create new if pool not maxed
            int maxPoolSize = 10;
            if (pool.size() < maxPoolSize) {
                ByteBuffer buffer = ByteBuffer.allocate(poolSize);
                PoolEntry entry = new PoolEntry(buffer);
                entry.inUse = true;
                pool.add(entry);
                return (ByteBuffer) buffer.limit(length);
            }
        }

        // Fallback: create non-pooled buffer
        return ByteBuffer.allocate(length);
    }

    /**
     * Release a buffer back to the pool
     */
    public void release(ByteBuffer buffer) {
        int actualSize = buffer.capacity();
        List<PoolEntry> pool = pools.get(actualSize);

        if (pool != null) {
            for (PoolEntry entry : pool) {
                if (entry.buffer == buffer) {
                    entry.inUse = false;
                    return;
                }
            }
        }
    }

    /**
     * Clear all pools
     */
    public void clear() {
        for (List<PoolEntry> pool : pools.values()) {
            pool.clear();
        }
    }

    /**
     * Stats record
     */
    public static class PoolStats {
        public final int size;
        public final int total;
        public final int inUse;

        public PoolStats(int size, int total, int inUse) {
            this.size = size;
            this.total = total;
            this.inUse = inUse;
        }
    }

    /**
     * Get pool statistics
     */
    public List<PoolStats> stats() {
        return pools.entrySet().stream()
                .map(entry -> {
                    int size = entry.getKey();
                    List<PoolEntry> pool = entry.getValue();
                    int total = pool.size();
                    int inUse = (int) pool.stream().filter(e -> e.inUse).count();
                    return new PoolStats(size, total, inUse);
                })
                .sorted(Comparator.comparingInt(a -> a.size))
                .collect(Collectors.toList());
    }

    // Singleton instance
    public static final BufferPool INSTANCE = new BufferPool();
}
