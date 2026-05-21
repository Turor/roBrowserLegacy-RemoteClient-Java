package turoran.grfloader.loader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Root object for path mapping JSON output.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PathMapping {
    private Instant generatedAt;
    private List<GRFStat> grfs;
    private Map<String, String> paths;
    private GRFSummary summary;
}
