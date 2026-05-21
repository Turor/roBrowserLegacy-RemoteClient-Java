package turoran.robrowser.client.model;

public record CacheStats(long size,
                         long maxSize,
                         String memoryUsedMB,
                         String maxMemoryMB,
                         long hits,
                         long misses,
                         String hitRate) {
}
