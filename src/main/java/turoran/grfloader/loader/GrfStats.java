package turoran.grfloader.loader;

/** GRF statistics */
public class GrfStats {
    /** Total file count */
    public int fileCount;
    /** Number of filenames with replacement character (U+FFFD) */
    public int badNameCount;
    /** Number of normalized key collisions */
    public int collisionCount;
    /** Extension statistics: ext -> count */
    public java.util.Map<String, Integer> extensionStats = new java.util.HashMap<>();
    /** Detected encoding used */
    public FilenameEncoding detectedEncoding;
}
