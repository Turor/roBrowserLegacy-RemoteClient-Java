package turoran.grfloader.loader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics for a single GRF file in PathMappingTool.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GRFStat {
    private String file;
    private int totalFiles;
    private int mapped;
    private int mojibake;
    private int c1;
    private String detectedEncoding;
}
