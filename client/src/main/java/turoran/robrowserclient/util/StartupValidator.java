package turoran.robrowserclient.util;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turoran.grfloader.loader.GRFNode;
import turoran.grfloader.loader.GrfStats;
import turoran.grfloader.loader.Decoder;
import turoran.robrowserclient.service.ClientService;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Startup validation system
 * Validates resources, configuration and dependencies before starting the server
 */
@Singleton
public class StartupValidator {
    private static final Logger logger = LoggerFactory.getLogger(StartupValidator.class);

    @Inject
    private ApplicationContext applicationContext;

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> info = new ArrayList<>();
    private final Map<String, Object> validationResults = new LinkedHashMap<>();

    @Value("${client.rootpath:.}")
    private String rootPath;

    @Value("${client.dataininame:DATA.INI}")
    private String dataIniName;

    @Value("${client.autoextract:false}")
    private boolean autoExtract;

    @Value("${client.enablesearch:true}")
    private boolean enableSearch;

    @Value("${client.usepathmappings:false}")
    private boolean usePathMappings;

    @Value("${client.cache.warmup.enabled:true}")
    private boolean warmUpEnabled;

    @Value("${client.cache.warmup.limit:500}")
    private int warmUpLimit;

    public void addError(String message) {
        errors.add(message);
    }

    public void addWarning(String message) {
        warnings.add(message);
    }

    public void addInfo(String message) {
        info.add(message);
    }

    public void validateJavaVersion() {
        try {
            String javaVersion = System.getProperty("java.version");
            String javaVendor = System.getProperty("java.vendor");
            String osName = System.getProperty("os.name");
            String osVersion = System.getProperty("os.version");
            String osArch = System.getProperty("os.arch");

            Map<String, Object> javaVersionInfo = new LinkedHashMap<>();
            javaVersionInfo.put("java", javaVersion);
            javaVersionInfo.put("vendor", javaVendor);
            javaVersionInfo.put("os", osName + " " + osVersion + " (" + osArch + ")");
            javaVersionInfo.put("valid", true);
            validationResults.put("javaVersion", javaVersionInfo);

            addInfo("Java: " + javaVersion + " (" + javaVendor + ")");
            addInfo("OS: " + osName + " " + osVersion + " (" + osArch + ")");

            int majorVersion = Runtime.version().feature();
            if (majorVersion < 17) {
                addWarning("Java version " + javaVersion + " may be too old. Recommended: v17 or newer");
            }
        } catch (Exception error) {
            addError("Failed to check Java version: " + error.getMessage());
            Map<String, Object> javaVersion = new HashMap<>();
            javaVersion.put("valid", false);
            javaVersion.put("error", error.getMessage());
            validationResults.put("javaVersion", javaVersion);
        }
    }

    public boolean validateDependencies() {
        // In Java/Gradle, dependencies are usually handled at build time.
        // We can check if certain classes are present.
        String[] requiredClasses = {
                "io.micronaut.runtime.Micronaut",
                "com.github.benmanes.caffeine.cache.Cache",
                "turoran.grfloader.loader.GRFNode"
        };
        List<String> missingDeps = new ArrayList<>();

        for (String className : requiredClasses) {
            try {
                Class.forName(className);
            } catch (ClassNotFoundException e) {
                missingDeps.add(className);
            }
        }

        if (!missingDeps.isEmpty()) {
            addError("Missing essential dependencies: " + String.join(", ", missingDeps));
            validationResults.put("dependencies", Map.of("installed", false, "missingDeps", missingDeps));
            return false;
        }

        addInfo("Dependencies verified correctly");
        validationResults.put("dependencies", Map.of("installed", true));
        return true;
    }

    public boolean validateGrfs() {
        Path resourcePath = Paths.get(rootPath, ClientService.RESOURCES_PATH);
        Path dataIniPath = resourcePath.resolve(dataIniName);

        if (!Files.exists(dataIniPath)) {
            addError("resources directory " + ClientService.RESOURCES_PATH + " not found at " + resourcePath.toAbsolutePath());
            validationResults.put("grfs", Map.of("valid", false, "reason", "DATA.INI missing"));
            return false;
        }

        try {
            // Using common RO encoding for DATA.INI
            String content = Files.readString(dataIniPath, Charset.forName("CP949"));
            List<String> grfFiles = parseDataINI(content);

            if (grfFiles.isEmpty()) {
                addError("No GRF files found in " + dataIniName + "!");
                validationResults.put("grfs", Map.of("valid", false, "reason", "No GRF files in DATA.INI"));
                return false;
            }

            List<Map<String, Object>> grfResults = new ArrayList<>();
            boolean hasInvalidGrf = false;

            for (String grfFile : grfFiles) {
                Path grfPath = resourcePath.resolve(grfFile);

                if (!Files.exists(grfPath)) {
                    addError("GRF not found: " + grfFile);
                    Map<String, Object> res = new HashMap<>();
                    res.put("file", grfFile);
                    res.put("exists", false);
                    grfResults.add(res);
                    hasInvalidGrf = true;
                    continue;
                }

                Map<String, Object> validation = validateGrfFormat(grfPath.toString());
                Map<String, Object> res = new HashMap<>(validation);
                res.put("file", grfFile);
                res.put("exists", true);
                grfResults.add(res);

                if (!(Boolean) validation.get("valid")) {
                    StringBuilder errorMsg = new StringBuilder("Incompatible GRF: " + grfFile + "\n");
                    if (validation.get("version") != null && !"unknown".equals(validation.get("version"))) {
                        errorMsg.append("  ❌ Version: ").append(validation.get("version")).append(" (expected: 0x200 or 0x300)\n");
                    }
                    errorMsg.append("  ❌ ").append(validation.get("reason")).append("\n");

                    @SuppressWarnings("unchecked")
                    Map<String, Object> fileTable = (Map<String, Object>) validation.get("fileTable");
                    if (fileTable != null && Boolean.FALSE.equals(fileTable.get("ok"))) {
                        errorMsg.append("  ❌ FileTable(zlib) failed: ").append(fileTable.get("reason")).append("\n");
                    }

                    errorMsg.append("\n  📦 FIX: Repack with GRF Builder:\n");
                    errorMsg.append("  1. Download GRF Builder: https://github.com/Tokeiburu/GRFEditor\n");
                    errorMsg.append("  2. Open GRF Builder\n");
                    errorMsg.append("  3. File → Options → Repack type → Decrypt\n");
                    errorMsg.append("  4. Click: Tools → Repack\n");
                    errorMsg.append("  5. Wait for completion and replace the original file");

                    addError(errorMsg.toString());
                    hasInvalidGrf = true;
                } else {
                    addInfo("Valid GRF: " + grfFile + " (version " + validation.get("version") + ")");

                    @SuppressWarnings("unchecked")
                    Map<String, Object> pathEncoding = (Map<String, Object>) validation.get("pathEncoding");
                    if (pathEncoding != null && "iso-8859-1".equals(pathEncoding.get("encoding"))) {
                        @SuppressWarnings("unchecked")
                        List<String> samples = (List<String>) pathEncoding.get("invalidUtf8Samples");
                        String samplesStr = (samples != null && !samples.isEmpty())
                                ? " Examples: " + String.join(" | ", samples)
                                : "";
                        addWarning("GRF path encoding: " + grfFile + " has non-UTF-8 filenames. Recommend legacy encoding fallback." + samplesStr);
                    }
                }
            }

            validationResults.put("grfs", Map.of(
                    "valid", !hasInvalidGrf,
                    "files", grfResults,
                    "count", grfFiles.size()
            ));

            return !hasInvalidGrf;

        } catch (IOException e) {
            addError("Failed to read DATA.INI: " + e.getMessage());
            return false;
        }
    }

    private List<String> parseDataINI(String content) {
        String[] lines = content.split("\\r?\\n");
        List<String> grfFiles = new ArrayList<>();
        boolean inDataSection = false;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue;

            if (line.equalsIgnoreCase("[data]")) {
                inDataSection = true;
                continue;
            }

            if (line.startsWith("[") && line.endsWith("]")) {
                inDataSection = false;
                continue;
            }

            if (inDataSection && line.contains("=")) {
                String[] parts = line.split("=", 2);
                if (parts.length > 1) {
                    String value = parts[1].trim();
                    if (value.toLowerCase().endsWith(".grf")) {
                        grfFiles.add(value);
                    }
                }
            }
        }

        return grfFiles;
    }

    private Map<String, Object> validateGrfFormat(String grfPath) {
        Map<String, Object> result = new HashMap<>();
        try (RandomAccessFile raf = new RandomAccessFile(grfPath, "r")) {
            long fileSize = raf.length();
            HeaderInfo headerInfo = readGrfHeader46(raf);

            if (!headerInfo.ok) {
                result.put("valid", false);
                result.put("version", "unknown");
                result.put("compatible", false);
                result.put("reason", headerInfo.reason);
                result.put("fileTable", Map.of("ok", false, "reason", "Skipped (invalid header)"));
                result.put("pathEncoding", Map.of("ok", false, "encoding", "unknown", "reason", "Skipped (invalid header)"));
                return result;
            }

            if (headerInfo.version != 0x200 && headerInfo.version != 0x300) {
                result.put("valid", false);
                result.put("version", headerInfo.versionHex);
                result.put("compatible", false);
                result.put("reason", "Version " + headerInfo.versionHex + " is not supported (expected: 0x200 or 0x300)");
                result.put("fileTable", Map.of("ok", false, "reason", "Skipped (unsupported version)"));
                result.put("pathEncoding", Map.of("ok", false, "encoding", "unknown", "reason", "Skipped (unsupported version)"));
                return result;
            }

            Map<String, Object> pathEncoding = analyzeGrfPathEncoding(raf, headerInfo, fileSize);
            boolean fileTableOk = Boolean.TRUE.equals(pathEncoding.get("ok"));

            Map<String, Object> fileTable = new HashMap<>();
            if (fileTableOk) {
                fileTable.put("ok", true);
                fileTable.put("layout", pathEncoding.get("layout"));
                @SuppressWarnings("unchecked")
                Map<String, Object> table = (Map<String, Object>) pathEncoding.get("table");
                if (table != null) {
                    fileTable.putAll(table);
                }
            } else {
                fileTable.put("ok", false);
                fileTable.put("reason", pathEncoding.get("reason"));
            }

            if (!fileTableOk) {
                result.put("valid", false);
                result.put("version", headerInfo.versionHex);
                result.put("compatible", false);
                result.put("reason", "Failed to read/parse compacted file table");
                result.put("fileTable", fileTable);
                result.put("pathEncoding", pathEncoding);
                return result;
            }

            // REAL TEST: try to load using the library
            try {
                // To be safe and matching original implementation logic, we use another RAF.
                try (RandomAccessFile testRaf = new RandomAccessFile(grfPath, "r")) {
                    GRFNode grf = new GRFNode(testRaf);
                    grf.load();
                }

                result.put("valid", true);
                result.put("version", headerInfo.versionHex);
                result.put("compatible", true);
                result.put("reason", "GRF loaded successfully by the library");
                result.put("fileTable", fileTable);
                result.put("pathEncoding", pathEncoding);
                return result;
            } catch (Exception loadError) {
                result.put("valid", false);
                result.put("version", headerInfo.versionHex);
                result.put("compatible", false);
                result.put("reason", "Library failed to load: " + loadError.getMessage());
                result.put("fileTable", fileTable);
                result.put("pathEncoding", pathEncoding);
                return result;
            }

        } catch (Exception error) {
            result.put("valid", false);
            result.put("version", "error");
            result.put("compatible", false);
            result.put("reason", "Failed to validate GRF: " + error.getMessage());
            result.put("fileTable", Map.of("ok", false, "reason", "Exception"));
            result.put("pathEncoding", Map.of("ok", false, "encoding", "unknown", "reason", "Exception"));
            return result;
        }
    }

    private static class HeaderInfo {
        boolean ok;
        String reason;
        long tableOffset;
        int seed;
        int nFiles;
        int fileCount;
        int version;
        String versionHex;
    }

    private HeaderInfo readGrfHeader46(RandomAccessFile raf) throws IOException {
        HeaderInfo info = new HeaderInfo();
        byte[] header = new byte[46];
        int n = raf.read(header);
        if (n < 46) {
            info.ok = false;
            info.reason = "Header too small (<46 bytes)";
            return info;
        }

        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        byte[] sigBytes = new byte[16];
        buffer.get(sigBytes);
        String signature = trimNullTerminatedAscii(sigBytes);

        if (!"Master of Magic".equals(signature)) {
            info.ok = false;
            info.reason = "Invalid signature: \"" + signature + "\"";
            return info;
        }

        buffer.position(30);
        info.tableOffset = buffer.getInt() & 0xFFFFFFFFL;
        info.seed = buffer.getInt();
        info.nFiles = buffer.getInt();
        info.version = buffer.getInt();

        info.fileCount = Math.max(info.nFiles - info.seed - 7, 0);
        info.versionHex = "0x" + Integer.toHexString(info.version).toUpperCase();
        info.ok = true;
        return info;
    }

    private String trimNullTerminatedAscii(byte[] bytes) {
        int len = 0;
        while (len < bytes.length && bytes[len] != 0) {
            len++;
        }
        return new String(bytes, 0, len, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private Map<String, Object> analyzeGrfPathEncoding(RandomAccessFile raf, HeaderInfo headerInfo, long fileSize) {
        String scanLimitStr = System.getenv("GRF_PATH_SCAN_LIMIT");
        int scanLimit = (scanLimitStr != null && scanLimitStr.matches("\\d+")) ? Integer.parseInt(scanLimitStr) : 0;

        long fileTablePos = headerInfo.tableOffset + 46;
        Map<String, Object> tableResult = inflateFileTable(raf, fileTablePos);

        if (!Boolean.TRUE.equals(tableResult.get("ok"))) {
            return Map.of(
                    "ok", false,
                    "encoding", "unknown",
                    "reason", tableResult.getOrDefault("reason", "unknown")
            );
        }

        byte[] tableData = (byte[]) tableResult.get("data");
        List<ScanResult> scans = new ArrayList<>();

        scans.add(scanFileTableNames(tableData, headerInfo.fileCount, 4, fileSize, scanLimit));

        if (headerInfo.version == 0x300) {
            scans.add(scanFileTableNames(tableData, headerInfo.fileCount, 8, fileSize, scanLimit));
        }

        scans.sort((a, b) -> {
            if (b.inspected != a.inspected) return b.inspected - a.inspected;
            if (a.parseErrors != b.parseErrors) return a.parseErrors - b.parseErrors;
            return a.offsetOutOfRange - b.offsetOutOfRange;
        });

        ScanResult best = scans.getFirst();

        if (best == null || best.inspected == 0) {
            return Map.of(
                    "ok", true,
                    "encoding", "unknown",
                    "reason", "No file entries inspected (table parse mismatch or empty GRF)",
                    "table", Map.of(
                            "compressedSize", tableResult.get("compressedSize"),
                            "uncompressedSize", tableResult.get("uncompressedSize")
                    )
            );
        }

        Map<String, Object> res = new HashMap<>();
        res.put("ok", true);
        res.put("encoding", best.invalidUtf8Count > 0 ? "iso-8859-1" : "utf-8");
        res.put("layout", best.layout);
        res.put("totalFilesInspected", best.inspected);
        res.put("invalidUtf8Count", best.invalidUtf8Count);
        res.put("invalidUtf8Samples", best.invalidUtf8Samples);
        res.put("parseErrors", best.parseErrors);
        res.put("offsetOutOfRange", best.offsetOutOfRange);
        res.put("table", Map.of(
                "compressedSize", tableResult.get("compressedSize"),
                "uncompressedSize", tableResult.get("uncompressedSize")
        ));
        res.put("note", best.invalidUtf8Count > 0
                ? "Detected non-UTF-8 filename bytes (legacy encoding)."
                : "All inspected filenames are valid UTF-8.");

        return res;
    }

    private Map<String, Object> inflateFileTable(RandomAccessFile raf, long fileTablePos) {
        try {
            raf.seek(fileTablePos);
            byte[] header = new byte[8];
            if (raf.read(header) < 8) {
                return Map.of("ok", false, "reason", "File table header too small (<8 bytes)");
            }

            ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            int compressedSize = buffer.getInt();
            int uncompressedSize = buffer.getInt();

            if (compressedSize == 0 || uncompressedSize == 0) {
                return Map.of("ok", false, "reason", "Invalid file table sizes (0)");
            }

            if (uncompressedSize > 512 * 1024 * 1024) {
                return Map.of("ok", false, "reason", "Uncompressed file table too large (" + uncompressedSize + " bytes)");
            }

            byte[] compressed = new byte[compressedSize];
            if (raf.read(compressed) < compressedSize) {
                return Map.of("ok", false, "reason", "Failed reading compressed file table bytes");
            }

            Inflater inflater = new Inflater();
            inflater.setInput(compressed);
            byte[] uncompressed = new byte[uncompressedSize];
            int resultLength = inflater.inflate(uncompressed);
            inflater.end();

            if (resultLength != uncompressedSize) {
                // sometimes it might be slightly different if not all bytes were used,
                // but for GRF it should match.
            }

            Map<String, Object> res = new HashMap<>();
            res.put("ok", true);
            res.put("compressedSize", compressedSize);
            res.put("uncompressedSize", uncompressedSize);
            res.put("data", uncompressed);
            return res;

        } catch (IOException | DataFormatException e) {
            return Map.of("ok", false, "reason", "zlib inflate failed: " + e.getMessage());
        }
    }

    private static class ScanResult {
        String layout;
        int inspected;
        int invalidUtf8Count;
        List<String> invalidUtf8Samples = new ArrayList<>();
        int parseErrors;
        int offsetOutOfRange;
    }

    private ScanResult scanFileTableNames(byte[] tableBuf, int fileCount, int offsetSize, long fileSize, int scanLimit) {
        ScanResult result = new ScanResult();
        result.layout = (offsetSize == 4) ? "offset32" : "offset64";

        int metaLen = 4 + 4 + 4 + 1 + offsetSize;
        int maxI = scanLimit > 0 ? Math.min(fileCount, scanLimit) : fileCount;

        int p = 0;
        ByteBuffer buffer = ByteBuffer.wrap(tableBuf).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < maxI; i++) {
            if (p >= tableBuf.length) {
                result.parseErrors++;
                break;
            }

            int end = p;
            while (end < tableBuf.length && tableBuf[end] != 0) end++;

            if (end >= tableBuf.length) {
                result.parseErrors++;
                break;
            }

            byte[] nameBytes = Arrays.copyOfRange(tableBuf, p, end);
            p = end + 1;

            if (p + metaLen > tableBuf.length) {
                result.parseErrors++;
                break;
            }

            // Skip compSize(4)
            int compAligned = buffer.getInt(p + 4);
            int flags = tableBuf[p + 12] & 0xFF;

            long offsetVal;
            if (offsetSize == 4) {
                offsetVal = buffer.getInt(p + 13) & 0xFFFFFFFFL;
            } else {
                offsetVal = buffer.getLong(p + 13);
            }

            p += metaLen;

            if ((flags & 0x01) == 0) continue;

            result.inspected++;

            if (fileSize > 0) {
                if (offsetVal < 0 || offsetVal >= fileSize) result.offsetOutOfRange++;
                else if (offsetVal + compAligned > fileSize) result.offsetOutOfRange++;
            }

            if (!isValidUtf8(nameBytes)) {
                result.invalidUtf8Count++;
                if (result.invalidUtf8Samples.size() < 5) {
                    result.invalidUtf8Samples.add(new String(nameBytes, StandardCharsets.ISO_8859_1));
                }
            }
        }

        return result;
    }

    private boolean isValidUtf8(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> validateEncodingDeep(List<String> grfFiles) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalFiles", 0);
        summary.put("badUfffd", 0);
        summary.put("badC1Control", 0);
        summary.put("mojibakeDetected", 0);
        summary.put("needsConversion", 0);
        summary.put("healthPercent", 100.0);

        List<Map<String, Object>> grfResults = new ArrayList<>();
        List<Map<String, Object>> filesToConvert = new ArrayList<>();

        Path dataPath = Paths.get(rootPath, ClientService.DATA_PATH);
        for (String grfFile : grfFiles) {
            Path grfPath = dataPath.resolve(grfFile);
            if (!Files.exists(grfPath)) continue;

            Map<String, Object> grfResult = new HashMap<>();
            grfResult.put("file", grfFile);
            grfResult.put("totalFiles", 0);
            grfResult.put("badUfffd", 0);
            grfResult.put("badC1Control", 0);
            grfResult.put("mojibakeDetected", 0);

            Map<String, List<Object>> examples = new HashMap<>();
            examples.put("badUfffd", new ArrayList<>());
            examples.put("badC1Control", new ArrayList<>());
            examples.put("mojibake", new ArrayList<>());
            grfResult.put("examples", examples);

            try (RandomAccessFile raf = new RandomAccessFile(grfPath.toString(), "r")) {
                GRFNode grf = new GRFNode(raf);
                grf.load();

                GrfStats stats = grf.getStats();
                grfResult.put("totalFiles", stats.fileCount);
                grfResult.put("detectedEncoding", stats.detectedEncoding != null ? stats.detectedEncoding.name() : "unknown");

                for (String filename : grf.listFiles()) {
                    int badUfffd = Decoder.countReplacementChars(filename);
                    int badC1 = Decoder.countC1ControlChars(filename);
                    boolean isMojibake = Decoder.isMojibake(filename);

                    if (badUfffd > 0) {
                        grfResult.put("badUfffd", (int) grfResult.get("badUfffd") + 1);
                        if (examples.get("badUfffd").size() < 10) examples.get("badUfffd").add(filename);
                    }
                    if (badC1 > 0) {
                        grfResult.put("badC1Control", (int) grfResult.get("badC1Control") + 1);
                        if (examples.get("badC1Control").size() < 10) examples.get("badC1Control").add(filename);
                    }
                    if (isMojibake) {
                        grfResult.put("mojibakeDetected", (int) grfResult.get("mojibakeDetected") + 1);
                        if (examples.get("mojibake").size() < 10) {
                            String fixed = Decoder.fixMojibake(filename);
                            examples.get("mojibake").add(Map.of("grfPath", filename, "koreanPath", fixed));
                        }
                    }
                }

                summary.put("totalFiles", (int) summary.get("totalFiles") + (int) grfResult.get("totalFiles"));
                summary.put("badUfffd", (int) summary.get("badUfffd") + (int) grfResult.get("badUfffd"));
                summary.put("badC1Control", (int) summary.get("badC1Control") + (int) grfResult.get("badC1Control"));
                summary.put("mojibakeDetected", (int) summary.get("mojibakeDetected") + (int) grfResult.get("mojibakeDetected"));

                int needsConv = (int) grfResult.get("mojibakeDetected") + (int) grfResult.get("badC1Control");
                summary.put("needsConversion", (int) summary.get("needsConversion") + needsConv);

                for (Object ex : examples.get("mojibake")) {
                    if (filesToConvert.size() < 50) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> conv = new HashMap<>((Map<String, String>) ex);
                        conv.put("grf", grfFile);
                        filesToConvert.add(conv);
                    }
                }

                grfResults.add(grfResult);

            } catch (Exception e) {
                grfResult.put("error", e.getMessage());
                grfResults.add(grfResult);
            }
        }

        int total = (int) summary.get("totalFiles");
        if (total > 0) {
            int badCount = (int) summary.get("badUfffd") + (int) summary.get("badC1Control");
            double health = ((double) (total - badCount) / total) * 100.0;
            summary.put("healthPercent", Math.round(health * 10000.0) / 10000.0);
        }

        Map<String, Object> results = new HashMap<>();
        results.put("grfs", grfResults);
        results.put("summary", summary);
        results.put("filesToConvert", filesToConvert);

        validationResults.put("encoding", results);
        return results;
    }

    public boolean validateRequiredFiles() {
        List<Map<String, Object>> checks = List.of(
                Map.of("path", rootPath, "type", "dir", "required", true, "name", "Root folder"),
                Map.of("path", Paths.get(rootPath, ClientService.DATA_PATH, dataIniName).toString(), "type", "file", "required", true, "name", dataIniName + " file"),
                Map.of("path", Paths.get(rootPath, "BGM").toString(), "type", "dir", "required", false, "name", "BGM folder"),
                Map.of("path", Paths.get(rootPath, ClientService.DATA_PATH).toString(), "type", "dir", "required", true, "name", ClientService.DATA_PATH + " folder"),
                Map.of("path", Paths.get(rootPath, "System").toString(), "type", "dir", "required", false, "name", "System folder"),
                Map.of("path", Paths.get(rootPath, "AI").toString(), "type", "dir", "required", false, "name", "AI folder"),
                Map.of("path", Paths.get(rootPath, "resources").toString(), "type", "dir", "required", false, "name", "Resources folder")
        );

        boolean hasErrors = false;
        List<Map<String, Object>> results = new ArrayList<>();

        for (Map<String, Object> check : checks) {
            String checkPath = (String) check.get("path");
            Path fullPath = Paths.get(checkPath);

            boolean exists = Files.exists(fullPath);
            Map<String, Object> res = new HashMap<>(check);
            res.put("exists", exists);

            if ("dir".equals(check.get("type"))) {
                boolean isEmpty = true;
                if (exists) {
                    try {
                        isEmpty = Files.list(fullPath).filter(p -> !p.getFileName().toString().startsWith("add-")).findAny().isEmpty();
                    } catch (IOException e) {
                        isEmpty = true;
                    }
                }
                res.put("isEmpty", isEmpty);

                if ((Boolean) check.get("required") && !exists) {
                    addError(check.get("name") + " not found!");
                    hasErrors = true;
                } else if ((Boolean) check.get("required") && isEmpty) {
                    addWarning(check.get("name") + " is empty");
                } else if (!(Boolean) check.get("required") && isEmpty) {
                    addWarning(check.get("name") + " is empty - may cause issues depending on the client");
                } else {
                    addInfo(check.get("name") + " OK");
                }
            } else {
                if ((Boolean) check.get("required") && !exists) {
                    addError(check.get("name") + " not found!");
                    hasErrors = true;
                } else if (exists) {
                    addInfo(check.get("name") + " OK");
                }
            }
            results.add(res);
        }

        validationResults.put("files", Map.of("valid", !hasErrors, "checks", results));
        return !hasErrors;
    }

    @Value("${micronaut.server.port:3338}")
    private String clientPublicUrl;

    public boolean validateEnvironment() {
        // In Micronaut, we check Environment and system variables
        Map<String, String> envVars = new HashMap<>();
        envVars.put("PORT", System.getenv("PORT"));
        envVars.put("CLIENT_PUBLIC_URL", clientPublicUrl);

        boolean hasErrors = false;
        Map<String, Object> results = new HashMap<>();

        if (envVars.get("PORT") == null) {
            addWarning("PORT not set, using default: 3338");
            results.put("PORT", Map.of("defined", false, "usingDefault", true, "value", "3338"));
        } else {
            addInfo("PORT: " + envVars.get("PORT"));
            results.put("PORT", Map.of("defined", true, "value", envVars.get("PORT")));
        }

        if (envVars.get("CLIENT_PUBLIC_URL") == null || envVars.get("CLIENT_PUBLIC_URL").isEmpty()) {
            addError("CLIENT_PUBLIC_URL not set! Configure it in the environment or application.yml");
            hasErrors = true;
            results.put("CLIENT_PUBLIC_URL", Map.of("defined", false, "error", "Variable not set"));
        } else {
            try {
                new URI(envVars.get("CLIENT_PUBLIC_URL")).toURL();
                addInfo("CLIENT_PUBLIC_URL: " + envVars.get("CLIENT_PUBLIC_URL"));
                results.put("CLIENT_PUBLIC_URL", Map.of("defined", true, "value", envVars.get("CLIENT_PUBLIC_URL")));
            } catch (URISyntaxException | IllegalArgumentException | java.net.MalformedURLException e) {
                addError("Invalid CLIENT_PUBLIC_URL: " + envVars.get("CLIENT_PUBLIC_URL"));
                hasErrors = true;
                results.put("CLIENT_PUBLIC_URL", Map.of("defined", true, "invalid", true, "value", envVars.get("CLIENT_PUBLIC_URL")));
            }
        }

        if (applicationContext != null) {
            Set<String> activeEnvs = applicationContext.getEnvironment().getActiveNames();
            String envsStr = activeEnvs.isEmpty() ? "none" : String.join(", ", activeEnvs);
            addInfo("Micronaut Environments: [" + envsStr + "]");
            results.put("MICRONAUT_ENVIRONMENTS", activeEnvs);
        }

        // Validate WS_ALLOWED_TARGETS when the proxy is enabled
        boolean wsProxyEnabled = "true".equalsIgnoreCase(System.getenv("ENABLE_WSPROXY"));
        String wsAllowedRaw = System.getenv("WS_ALLOWED_TARGETS");
        if (wsProxyEnabled) {
            if (wsAllowedRaw != null && !wsAllowedRaw.isEmpty()) {
                String[] entries = wsAllowedRaw.split(",");
                List<String> validEntries = new ArrayList<>();
                List<String> invalidEntries = new ArrayList<>();
                for (String entry : entries) {
                    entry = entry.trim();
                    if (entry.isEmpty()) continue;
                    int colonIdx = entry.lastIndexOf(':');
                    if (colonIdx == -1) {
                        invalidEntries.add(entry);
                        continue;
                    }
                    try {
                        int port = Integer.parseInt(entry.substring(colonIdx + 1));
                        String host = entry.substring(0, colonIdx);
                        if (host.isEmpty() || port < 1 || port > 65535) {
                            invalidEntries.add(entry);
                        } else {
                            validEntries.add(entry);
                        }
                    } catch (NumberFormatException e) {
                        invalidEntries.add(entry);
                    }
                }
                if (!invalidEntries.isEmpty()) {
                    addError("WS_ALLOWED_TARGETS contains invalid entries: " + String.join(", ", invalidEntries) +
                            "\n  Each entry must be \"host:port\" where port is 1-65535.");
                    hasErrors = true;
                } else {
                    addInfo("WS_ALLOWED_TARGETS: " + String.join(", ", validEntries));
                }
                results.put("WS_ALLOWED_TARGETS", Map.of("defined", true, "entries", validEntries));
            } else {
                addInfo("WS_ALLOWED_TARGETS: not set, using localhost defaults (127.0.0.1:6900/6121/5121)");
                results.put("WS_ALLOWED_TARGETS", Map.of("defined", false, "usingDefaults", true));
            }
        }

        validationResults.put("env", Map.of("valid", !hasErrors, "variables", results));
        return !hasErrors;
    }

    public Map<String, Object> validateAll(boolean deepEncoding) {
        errors.clear();
        warnings.clear();
        info.clear();
        validationResults.clear();

        logger.info("🔍 Validating startup configuration...");

        validateJavaVersion();

        if (!validateDependencies()) return getResults();

        validateRequiredFiles();
        validateEnvironment();
        validateGrfs();

        boolean pathMappingExists = Files.exists(Paths.get(rootPath, "resources", "path-mapping.json"));
        addInfo("Path mapping file: " + (pathMappingExists ? "Found" : "Not found (optional)"));
        validationResults.put("pathMapping", Map.of("exists", pathMappingExists));

        if (deepEncoding && validationResults.get("grfs") != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> grfData = (Map<String, Object>) validationResults.get("grfs");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> files = (List<Map<String, Object>>) grfData.get("files");
            if (files != null) {
                List<String> validGrfFiles = new ArrayList<>();
                for (Map<String, Object> f : files) {
                    if (Boolean.TRUE.equals(f.get("exists")) && Boolean.TRUE.equals(f.get("valid"))) {
                        validGrfFiles.add((String) f.get("file"));
                    }
                }

                if (!validGrfFiles.isEmpty()) {
                    logger.info("🔍 Running deep encoding validation...");
                    Map<String, Object> encodingResults = validateEncodingDeep(validGrfFiles);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> summary = (Map<String, Object>) encodingResults.get("summary");

                    if ((int) summary.get("mojibakeDetected") > 0) {
                        addWarning("Legacy encoding mojibake detected: " + summary.get("mojibakeDetected") + " files need encoding conversion");
                    }
                    if ((int) summary.get("badUfffd") > 0) {
                        addWarning("U+FFFD characters: " + summary.get("badUfffd") + " files have replacement characters");
                    }
                    if ((int) summary.get("badC1Control") > 0) {
                        addWarning("C1 Control chars: " + summary.get("badC1Control") + " files have C1 control characters");
                    }

                    addInfo("Encoding health: " + summary.get("healthPercent") + "% (" + summary.get("totalFiles") + " files)");
                }
            }
        }

        return getResults();
    }

    public Map<String, Object> getResults() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", errors.isEmpty());
        res.put("errors", new ArrayList<>(errors));
        res.put("warnings", new ArrayList<>(warnings));
        res.put("info", new ArrayList<>(info));
        res.put("details", new HashMap<>(validationResults));
        return res;
    }

    private String formatMultiline(List<String> messages) {
        StringBuilder sb = new StringBuilder();
        for (String msg : messages) {
            for (String line : msg.split("\\n")) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append("  ").append(line);
            }
        }
        return sb.toString();
    }

    public void printReport(Map<String, Object> results) {
        if (results == null) results = getResults();
        StringBuilder sb = new StringBuilder();

        sb.append("📋 VALIDATION REPORT");

        @SuppressWarnings("unchecked")
        List<String> infoList = (List<String>) results.get("info");
        if (infoList != null && !infoList.isEmpty()) {
            sb.append("\n✓ INFO:\n").append(formatMultiline(infoList));
        }

        @SuppressWarnings("unchecked")
        List<String> warningList = (List<String>) results.get("warnings");
        if (warningList != null && !warningList.isEmpty()) {
            sb.append("\n⚠️  WARNINGS:\n").append(formatMultiline(warningList));
        }

        @SuppressWarnings("unchecked")
        List<String> errorList = (List<String>) results.get("errors");
        if (errorList != null && !errorList.isEmpty()) {
            sb.append("\n❌ ERRORS:\n").append(formatMultiline(errorList));
        }

        boolean success = (boolean) results.get("success");
        if (success) {
            sb.append("\n✅ Validation completed successfully!");
            if (!warningList.isEmpty()) sb.append("⚠️  ").append(warningList.size()).append(" warning(s) found");
        } else {
            sb.append("\n❌ Validation failed!").append(errorList.size()).append(" error(s) found");
            sb.append("\n💡 Tip: Check logs for detailed diagnosis");
        }

        logger.info(sb.toString());
    }

    public Map<String, Object> getStatusJSON() {
        Map<String, Object> results = getResults();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("timestamp", Instant.now().toString());
        status.put("status", (boolean) results.get("success") ? "ok" : "error");
        status.put("hasWarnings", !((List<?>) results.get("warnings")).isEmpty());

        Map<String, Integer> summary = new HashMap<>();
        summary.put("errors", ((List<?>) results.get("errors")).size());
        summary.put("warnings", ((List<?>) results.get("warnings")).size());
        summary.put("info", ((List<?>) results.get("info")).size());
        status.put("summary", summary);

        status.put("details", results.get("details"));
        status.put("messages", Map.of(
                "errors", results.get("errors"),
                "warnings", results.get("warnings"),
                "info", results.get("info")
        ));

        return status;
    }
}
