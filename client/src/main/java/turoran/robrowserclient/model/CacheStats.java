package turoran.robrowserclient.model;

public record CacheStats(long size,
                         long maxSize,
                         String memoryUsedMB,
                         String maxMemoryMB,
                         long hits,
                         long misses,
                         String hitRate) {
}
