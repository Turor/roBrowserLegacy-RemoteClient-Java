package turoran.grfloader.loader;

/** GRF loader options */
public class GrfOptions {
    /** Encoding for filenames (default: 'auto') */
    public FilenameEncoding filenameEncoding = FilenameEncoding.AUTO;
    /** Threshold for auto-detection: if % of U+FFFD exceeds this, try Korean encodings (default: 0.01 = 1%) */
    public double autoDetectThreshold = 0.01;
    /** Maximum uncompressed size per file in bytes (default: 256MB) */
    public long maxFileUncompressedBytes = 256 * 1024 * 1024;
    /** Maximum total entries allowed (default: 500000) */
    public int maxEntries = 500000;
}
