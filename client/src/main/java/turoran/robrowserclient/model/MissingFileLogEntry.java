package turoran.robrowserclient.model;

import java.time.Instant;

public record MissingFileLogEntry(
        Instant timestamp,
        String requestedPath,
        String grfPath,
        String mappedPath
) {}
