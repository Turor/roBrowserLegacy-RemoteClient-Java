package turoran.grfloader.loader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

// ============================================================================
// Types and Interfaces
// ============================================================================

// ============================================================================
// GrfBase Class
// ============================================================================

public abstract class GRFBase<T> {
    private static final String HEADER_SIGNATURE = "Master of Magic";
    private static final int HEADER_SIZE = 46;
    private static final int FILE_TABLE_SIZE = 8; // 2 * 4 bytes (Uint32Array.BYTES_PER_ELEMENT * 2)

    private static final int FILELIST_TYPE_FILE = 0x01;
    private static final int FILELIST_TYPE_ENCRYPT_MIXED = 0x02; // encryption mode 0
    private static final int FILELIST_TYPE_ENCRYPT_HEADER = 0x04; // encryption mode 1

    public int version = 0x200;
    public int fileCount = 0;
    public boolean loaded = false;

    /** Map of exact filename -> entry */
    public final Map<String, turoran.grfloader.loader.TFileEntry> files = new HashMap<>();

    /** Map of normalized path -> array of exact filenames (supports collisions) */
    private final Map<String, List<String>> normalizedIndex = new HashMap<>();

    /** Map of extension -> array of exact filenames (for fast extension lookup) */
    private final Map<String, List<String>> extensionIndex = new HashMap<>();

    private long fileTableOffset = 0;
    private final Map<String, byte[]> cache = new LinkedHashMap<String, byte[]>(50, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > 50;
        }
    };

    // Options
    protected GrfOptions options;

    // Statistics
    private final GrfStats stats = new GrfStats();

    protected final T fd;

    public GRFBase(T fd) {
        this(fd, new GrfOptions());
    }

    public GRFBase(T fd, GrfOptions options) {
        this.fd = fd;
        this.options = options != null ? options : new GrfOptions();
    }

    public abstract byte[] getStreamBuffer(T fd, long offset, int length) throws IOException;

    public byte[] getStreamBuffer(long offset, int length) throws IOException {
        return getStreamBuffer(this.fd, offset, length);
    }

    public void load() throws IOException {
        if (!this.loaded) {
            this.parseHeader();
            this.parseFileList();
            this.loaded = true;
        }
    }

    private void parseHeader() throws IOException {
        byte[] header = getStreamBuffer(0, HEADER_SIZE);
        ByteBuffer reader = ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN);

        byte[] sigBytes = new byte[15];
        reader.get(sigBytes);
        String signature = new String(sigBytes).trim();
        if (!HEADER_SIGNATURE.equals(signature)) {
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("signature", signature);
            throw new turoran.grfloader.loader.GrfError(turoran.grfloader.loader.GrfErrorCode.INVALID_MAGIC, "Not a GRF file (invalid signature)", ctx);
        }

        reader.position(30);
        // Version is at offset 42.
        int afterKey = reader.position();
        reader.position(42);
        this.version = reader.getInt();

        if (this.version != 0x200 && this.version != 0x300) {
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("version", this.version);
            throw new turoran.grfloader.loader.GrfError(turoran.grfloader.loader.GrfErrorCode.UNSUPPORTED_VERSION, "Unsupported version \"0x" + Integer.toHexString(this.version) + "\"", ctx);
        }

        reader.position(afterKey);

        if (this.version == 0x200) {
            // 0x200: [table_offset:u32][seeds:u32][filecount:u32][version:u32]
            this.fileTableOffset = (reader.getInt() & 0xFFFFFFFFL) + HEADER_SIZE;
            int reservedFiles = reader.getInt();
            this.fileCount = reader.getInt() - reservedFiles - 7;
        } else {
            // 0x300: [table_offset:u64][filecount:u32][version:u32]
            long low = reader.getInt() & 0xFFFFFFFFL;
            long high = reader.getInt() & 0xFFFFFFFFL;

            if ((high >>> 8) != 0) {
                // Fall back to 0x200
                this.version = 0x200;
                reader.position(afterKey);
                this.fileTableOffset = (reader.getInt() & 0xFFFFFFFFL) + HEADER_SIZE;
                int reservedFiles = reader.getInt();
                this.fileCount = reader.getInt() - reservedFiles - 7;
            } else {
                this.fileTableOffset = (high << 32) + low + HEADER_SIZE;
                this.fileCount = reader.getInt();
            }
        }

        if (this.fileCount > this.options.maxEntries) {
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("fileCount", this.fileCount);
            ctx.put("maxEntries", this.options.maxEntries);
            throw new turoran.grfloader.loader.GrfError(turoran.grfloader.loader.GrfErrorCode.LIMIT_EXCEEDED, "File count " + this.fileCount + " exceeds limit " + this.options.maxEntries, ctx);
        }
    }

    private void parseFileList() throws IOException {
        int tableSkip = (this.version == 0x300) ? 4 : 0;

        byte[] tableHeader = getStreamBuffer(this.fileTableOffset + tableSkip, FILE_TABLE_SIZE);
        ByteBuffer reader = ByteBuffer.wrap(tableHeader).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int compressedSize = reader.getInt();
        int realSize = reader.getInt();

        byte[] compressed = getStreamBuffer(this.fileTableOffset + tableSkip + FILE_TABLE_SIZE, compressedSize);

        byte[] data;
        try {
            data = inflate(compressed, realSize);
        } catch (DataFormatException e) {
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("compressedSize", compressedSize);
            ctx.put("realSize", realSize);
            ctx.put("error", e.getMessage());
            throw new turoran.grfloader.loader.GrfError(turoran.grfloader.loader.GrfErrorCode.CORRUPT_TABLE, "Failed to decompress file table", ctx);
        }

        if (data.length != realSize) {
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("expected", realSize);
            ctx.put("actual", data.length);
            throw new turoran.grfloader.loader.GrfError(turoran.grfloader.loader.GrfErrorCode.CORRUPT_TABLE, "File table size mismatch: expected " + realSize + ", got " + data.length, ctx);
        }

        FilenameEncoding detectedEncoding = this.options.filenameEncoding;

        if (this.options.filenameEncoding == FilenameEncoding.AUTO) {
            List<byte[]> sampleBytesList = new ArrayList<>();
            int samplePos = 0;
            int sampleCount = Math.min(200, this.fileCount);
            int entryDataSize = (this.version == 0x300) ? 21 : 17;

            for (int i = 0; i < sampleCount && samplePos < data.length; i++) {
                int endPos = samplePos;
                while (endPos < data.length && data[endPos] != 0) endPos++;

                byte[] bytes = Arrays.copyOfRange(data, samplePos, endPos);
                sampleBytesList.add(bytes);

                samplePos = endPos + 1 + entryDataSize;
            }

            byte[][] samples = sampleBytesList.toArray(new byte[0][]);
            String bestEncoding = Decoder.detectBestKoreanEncoding(samples, this.options.autoDetectThreshold);
            detectedEncoding = FilenameEncoding.fromString(bestEncoding);
        }

        this.stats.detectedEncoding = detectedEncoding;
        this.stats.badNameCount = 0;
        this.stats.collisionCount = 0;
        this.stats.extensionStats.clear();

        int entryDataSize = (this.version == 0x300) ? 21 : 17;
        int p = 0;
        for (int i = 0; i < this.fileCount; i++) {
            if (p >= data.length) {
                Map<String, Object> ctx = new HashMap<>();
                ctx.put("position", p);
                ctx.put("dataLength", data.length);
                ctx.put("entryIndex", i);
                throw new turoran.grfloader.loader.GrfError(turoran.grfloader.loader.GrfErrorCode.CORRUPT_TABLE, "Unexpected end of file table at entry " + i, ctx);
            }

            int endPos = p;
            while (endPos < data.length && data[endPos] != 0) {
                endPos++;
            }

            byte[] rawBytes = Arrays.copyOfRange(data, p, endPos);
            String filename = Decoder.decodeBytes(rawBytes, detectedEncoding.getValue());

            if (Decoder.countBadChars(filename) > 0) {
                this.stats.badNameCount++;
            }

            p = endPos + 1;

            if (p + entryDataSize > data.length) {
                Map<String, Object> ctx = new HashMap<>();
                ctx.put("position", p);
                ctx.put("dataLength", data.length);
                ctx.put("entryIndex", i);
                throw new turoran.grfloader.loader.GrfError(turoran.grfloader.loader.GrfErrorCode.CORRUPT_TABLE, "Incomplete entry data at entry " + i, ctx);
            }

            int compSize = (data[p++] & 0xFF) | ((data[p++] & 0xFF) << 8) | ((data[p++] & 0xFF) << 16) | ((data[p++] & 0xFF) << 24);
            int lenAligned = (data[p++] & 0xFF) | ((data[p++] & 0xFF) << 8) | ((data[p++] & 0xFF) << 16) | ((data[p++] & 0xFF) << 24);
            int rSize = (data[p++] & 0xFF) | ((data[p++] & 0xFF) << 8) | ((data[p++] & 0xFF) << 16) | ((data[p++] & 0xFF) << 24);
            int type = data[p++] & 0xFF;

            long offset;
            if (this.version == 0x300) {
                long low = (data[p++] & 0xFFL) | ((data[p++] & 0xFFL) << 8) | ((data[p++] & 0xFFL) << 16) | ((data[p++] & 0xFFL) << 24);
                long high = (data[p++] & 0xFFL) | ((data[p++] & 0xFFL) << 8) | ((data[p++] & 0xFFL) << 16) | ((data[p++] & 0xFFL) << 24);
                offset = (high << 32) | (low & 0xFFFFFFFFL);
            } else {
                offset = (data[p++] & 0xFFL) | ((data[p++] & 0xFFL) << 8) | ((data[p++] & 0xFFL) << 16) | ((data[p++] & 0xFFL) << 24);
                offset &= 0xFFFFFFFFL;
            }

            turoran.grfloader.loader.TFileEntry entry = new turoran.grfloader.loader.TFileEntry();
            entry.compressedSize = compSize;
            entry.lengthAligned = lenAligned;
            entry.realSize = rSize;
            entry.type = type;
            entry.offset = offset;
            entry.rawNameBytes = rawBytes;

            if (entry.realSize > this.options.maxFileUncompressedBytes) {
                continue;
            }

            if ((entry.type & FILELIST_TYPE_FILE) != 0) {
                this.files.put(filename, entry);

                String normalizedKey = Decoder.normalizePath(filename);
                List<String> existingNorm = this.normalizedIndex.computeIfAbsent(normalizedKey, k -> new ArrayList<>());
                if (!existingNorm.isEmpty()) {
                    this.stats.collisionCount++;
                }
                existingNorm.add(filename);

                String ext = getExtension(filename);
                if (!ext.isEmpty()) {
                    this.extensionIndex.computeIfAbsent(ext, k -> new ArrayList<>()).add(filename);
                    this.stats.extensionStats.put(ext, this.stats.extensionStats.getOrDefault(ext, 0) + 1);
                }
            }
        }
        this.stats.fileCount = this.files.size();
    }

    private byte[] decodeEntry(byte[] data, turoran.grfloader.loader.TFileEntry entry) throws DataFormatException {
        if ((entry.type & FILELIST_TYPE_ENCRYPT_MIXED) != 0) {
            DESDecoder.decodeFull(data, entry.lengthAligned, entry.compressedSize);
        } else if ((entry.type & FILELIST_TYPE_ENCRYPT_HEADER) != 0) {
            DESDecoder.decodeHeader(data, entry.lengthAligned);
        }

        if (entry.realSize == entry.compressedSize) {
            return data;
        }

        return inflate(data, entry.realSize);
    }

    private byte[] inflate(byte[] compressed, int realSize) throws DataFormatException {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        byte[] result = new byte[realSize];
        int resultLength = inflater.inflate(result);
        inflater.end();
        return result;
    }

    public void clearCache() {
        this.cache.clear();
    }

    public turoran.grfloader.loader.FileResult getFile(String filename) {
        if (!this.loaded) {
            return new turoran.grfloader.loader.FileResult(null, "GRF not loaded yet");
        }

        turoran.grfloader.loader.ResolveResult resolved = this.resolvePath(filename);

        if (resolved.status == turoran.grfloader.loader.ResolveResult.Status.NOT_FOUND) {
            return new turoran.grfloader.loader.FileResult(null, "File \"" + filename + "\" not found");
        }

        if (resolved.status == turoran.grfloader.loader.ResolveResult.Status.AMBIGUOUS) {
            return new turoran.grfloader.loader.FileResult(null, "Ambiguous path \"" + filename + "\": " + resolved.candidates.size() + " matches found.");
        }

        String path = resolved.matchedPath;

        synchronized (cache) {
            byte[] cached = cache.get(path);
            if (cached != null) {
                return new turoran.grfloader.loader.FileResult(cached, null);
            }
        }

        turoran.grfloader.loader.TFileEntry entry = this.files.get(path);
        if (entry == null) {
            return new turoran.grfloader.loader.FileResult(null, "File \"" + path + "\" not found");
        }

        try {
            byte[] data = getStreamBuffer(entry.offset + HEADER_SIZE, entry.lengthAligned);
            byte[] result = decodeEntry(data, entry);

            synchronized (cache) {
                cache.put(path, result);
            }

            return new turoran.grfloader.loader.FileResult(result, null);
        } catch (Exception e) {
            return new turoran.grfloader.loader.FileResult(null, e.getMessage());
        }
    }

    public turoran.grfloader.loader.ResolveResult resolvePath(String query) {
        if (this.files.containsKey(query)) {
            return new turoran.grfloader.loader.ResolveResult(turoran.grfloader.loader.ResolveResult.Status.FOUND, query);
        }

        String normalizedQuery = Decoder.normalizePath(query);
        List<String> candidates = this.normalizedIndex.get(normalizedQuery);

        if (candidates == null || candidates.isEmpty()) {
            return new turoran.grfloader.loader.ResolveResult(turoran.grfloader.loader.ResolveResult.Status.NOT_FOUND);
        }

        if (candidates.size() == 1) {
            return new turoran.grfloader.loader.ResolveResult(turoran.grfloader.loader.ResolveResult.Status.FOUND, candidates.get(0));
        }

        return new turoran.grfloader.loader.ResolveResult(turoran.grfloader.loader.ResolveResult.Status.AMBIGUOUS, candidates);
    }

    public boolean hasFile(String filename) {
        return resolvePath(filename).status == turoran.grfloader.loader.ResolveResult.Status.FOUND;
    }

    public turoran.grfloader.loader.TFileEntry getEntry(String filename) {
        turoran.grfloader.loader.ResolveResult resolved = resolvePath(filename);
        if (resolved.status != turoran.grfloader.loader.ResolveResult.Status.FOUND || resolved.matchedPath == null) {
            return null;
        }
        return this.files.get(resolved.matchedPath);
    }

    public List<String> find(turoran.grfloader.loader.FindOptions options) {
        if (options == null) options = new turoran.grfloader.loader.FindOptions();
        
        List<String> results = new ArrayList<>();

        if (options.ext != null && options.contains == null && options.endsWith == null && options.regex == null) {
            String extLower = options.ext.toLowerCase();
            if (extLower.startsWith(".")) extLower = extLower.substring(1);
            List<String> extFiles = this.extensionIndex.get(extLower);
            if (extFiles != null) {
                results.addAll(extFiles);
            }
        } else {
            for (String filename : this.files.keySet()) {
                if (options.ext != null) {
                    String extLower = options.ext.toLowerCase();
                    if (extLower.startsWith(".")) extLower = extLower.substring(1);
                    if (!getExtension(filename).equals(extLower)) continue;
                }

                if (options.contains != null) {
                    String normalizedFilename = Decoder.normalizePath(filename);
                    String normalizedContains = Decoder.normalizePath(options.contains);
                    if (!normalizedFilename.contains(normalizedContains)) continue;
                }

                if (options.endsWith != null) {
                    String normalizedFilename = Decoder.normalizePath(filename);
                    String normalizedEndsWith = Decoder.normalizePath(options.endsWith);
                    if (!normalizedFilename.endsWith(normalizedEndsWith)) continue;
                }

                if (options.regex != null && !options.regex.matcher(filename).find()) continue;

                results.add(filename);

                if (options.limit != null && results.size() >= options.limit) break;
            }
        }

        if (options.limit != null && results.size() > options.limit) {
            results = results.subList(0, options.limit);
        }

        return results;
    }

    public List<String> getFilesByExtension(String ext) {
        String extLower = ext.toLowerCase();
        if (extLower.startsWith(".")) extLower = extLower.substring(1);
        List<String> list = this.extensionIndex.get(extLower);
        return list != null ? new ArrayList<>(list) : new ArrayList<>();
    }

    public List<String> listExtensions() {
        List<String> exts = new ArrayList<>(this.extensionIndex.keySet());
        Collections.sort(exts);
        return exts;
    }

    public List<String> listFiles() {
        return new ArrayList<>(this.files.keySet());
    }

    public GrfStats getStats() {
        GrfStats copy = new GrfStats();
        copy.fileCount = this.stats.fileCount;
        copy.badNameCount = this.stats.badNameCount;
        copy.collisionCount = this.stats.collisionCount;
        copy.extensionStats = new HashMap<>(this.stats.extensionStats);
        copy.detectedEncoding = this.stats.detectedEncoding;
        return copy;
    }

    public FilenameEncoding getDetectedEncoding() {
        return this.stats.detectedEncoding;
    }

    public void reloadWithEncoding(FilenameEncoding encoding) throws IOException {
        this.options.filenameEncoding = encoding;
        this.files.clear();
        this.normalizedIndex.clear();
        this.extensionIndex.clear();
        this.clearCache();
        this.loaded = false;
        this.load();
    }

    private String getExtension(String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot == -1 || lastDot == path.length() - 1) return "";
        return path.substring(lastDot + 1).toLowerCase();
    }
}
