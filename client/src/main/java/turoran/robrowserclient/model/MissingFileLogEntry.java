package turoran.robrowserclient.model;

import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

@Serdeable
public record MissingFileLogEntry(
        Instant timestamp,
        String requestedPath,
        String grfPath,
        String mappedPath
) {}
