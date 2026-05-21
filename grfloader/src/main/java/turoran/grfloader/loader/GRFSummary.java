package turoran.grfloader.loader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary statistics for PathMappingTool.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GRFSummary {
    private int totalFiles;
    private int totalMapped;
    private int mojibakeFixed;
    private int c1Fixed;

    public void addFiles(int count) {
        this.totalFiles += count;
    }

    public void addMapped(int count) {
        this.totalMapped += count;
    }

    public void addMojibake(int count) {
        this.mojibakeFixed += count;
    }

    public void addC1(int count) {
        this.c1Fixed += count;
    }
}
