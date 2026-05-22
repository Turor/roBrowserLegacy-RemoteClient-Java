package turoran.robrowserclient.model;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CacheStats(long size,
                         long maxSize,
                         String memoryUsedMB,
                         String maxMemoryMB,
                         long hits,
                         long misses,
                         String hitRate) {
}
