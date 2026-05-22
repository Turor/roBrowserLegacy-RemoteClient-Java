package turoran.robrowserclient.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Value;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turoran.grfloader.tools.PathMappingTool;
import turoran.robrowserclient.model.CacheEntry;
import turoran.robrowserclient.model.MissingFileLogEntry;
import turoran.robrowserclient.util.StartupValidator;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Singleton
public class ClientService {
    private static final Logger logger = LoggerFactory.getLogger(ClientService.class);
    private static final Logger missingFileLogger = LoggerFactory.getLogger("missing-files");

    private final LRUCacheService fileCache;
    private final StartupValidator startupValidator;
    private final BeanContext beanContext;
    private static final String RESOURCES_PATH = "resources";
    private static final String BGM_PATH = "BGM";
    private static final String DATA_PATH = "Data";
    private static final String AI_PATH = "AI";
    private static final String SYSTEM_PATH = "System";

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

    private final List<GRFService> grfs = new ArrayList<>();
    private final Map<String, FileIndexEntry> fileIndex = new ConcurrentHashMap<>();
    private final List<FileIndexEntry> uniqueEntries = new ArrayList<>();
    private boolean indexBuilt = false;
    
    private final Set<String> missingFilesSet = ConcurrentHashMap.newKeySet();
    private final Deque<MissingFileLogEntry> missingFiles = new ArrayDeque<>();
    private final Object missingFilesLock = new Object();
    private long lastNotificationTime = 0;
    private static final long NOTIFICATION_COOLDOWN = 60000;

    private final Map<String, String> externalPathMapping = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private record FileIndexEntry(int grfIndex, String originalPath, String mappedFrom) {}

    public ClientService(LRUCacheService fileCache, StartupValidator startupValidator, BeanContext beanContext) {
        this.fileCache = fileCache;
        this.startupValidator = startupValidator;
        this.beanContext = beanContext;
    }

    @PostConstruct
    public void init() {
        // Create required directories if they don't exist
        try {
            Path root = Paths.get(rootPath);
            String[] requiredDirs = {BGM_PATH, DATA_PATH, AI_PATH, SYSTEM_PATH};
            for (String dir : requiredDirs) {
                Path p = root.resolve(dir);
                if (!Files.exists(p)) {
                    Files.createDirectories(p);
                    logger.info("Created directory: {}", p.toAbsolutePath());
                }
            }
        } catch (IOException e) {
            logger.error("Failed to create required directories", e);
        }

        startupValidator.printReport(startupValidator.validateAll(false));

        loadPathMapping();

        long startTime = System.currentTimeMillis();
        Path dataPath = Paths.get(rootPath, RESOURCES_PATH);
        Path dataIniPath = dataPath.resolve(dataIniName);

        if (!Files.exists(dataIniPath)) {
            logger.error("DATA.INI file not found: {}", dataIniPath.toAbsolutePath());
            return;
        }

        try {
            String content = Files.readString(dataIniPath, Charset.forName("CP949"));
            Map<String, List<String>> dataIni = parseIni(content);

            List<String> grfPaths = dataIni.getOrDefault("data", Collections.emptyList());
            if (grfPaths.isEmpty()) {
                logger.warn("No GRF files configured in DATA.INI.");
                return;
            }

            // Close existing GRFs before reloading
            for (GRFService grf : grfs) {
                grf.close();
            }
            grfs.clear();

            for (String grfName : grfPaths) {
                if (grfName == null || grfName.isBlank()) continue;
                String fullPath = dataPath.resolve(grfName).toString();
                GRFService grf = beanContext.createBean(GRFService.class, fullPath);
                grf.load();
                if (grf.isLoaded()) {
                    grfs.add(grf);
                }
            }

            buildFileIndex();

            if (warmUpEnabled) {
                warmCache(null, warmUpLimit);
            }

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Client initialized in {}ms ({} files indexed)", elapsed, fileIndex.size());
        } catch (IOException e) {
            logger.error("Failed to read DATA.INI", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        for (GRFService grf : grfs) {
            grf.close();
        }
    }

    private void buildFileIndex() {
        long startTime = System.currentTimeMillis();
        fileIndex.clear();
        uniqueEntries.clear();
        int mojibakeCount = 0;

        Charset cp949 = Charset.forName("CP949");
        Charset latin1 = StandardCharsets.ISO_8859_1;

        for (int i = 0; i < grfs.size(); i++) {
            GRFService grf = grfs.get(i);
            List<String> files = grf.listFiles();
            for (String file : files) {
                FileIndexEntry mainEntry = new FileIndexEntry(i, file, null);
                uniqueEntries.add(mainEntry);
                
                String normalized = file.toLowerCase().replace('\\', '/');
                fileIndex.putIfAbsent(normalized, mainEntry);

                String normalizedBackslash = file.toLowerCase().replace('/', '\\');
                fileIndex.putIfAbsent(normalizedBackslash, mainEntry);

                try {
                    byte[] cp949Bytes = file.getBytes(cp949);
                    String mojibakePath = new String(cp949Bytes, latin1);
                    if (!mojibakePath.equals(file)) {
                        String normalizedMojibake = mojibakePath.toLowerCase().replace('\\', '/');
                        if (fileIndex.putIfAbsent(normalizedMojibake, mainEntry) == null) {
                            mojibakeCount++;
                        }
                        String mojibakeBackslash = mojibakePath.toLowerCase().replace('/', '\\');
                        fileIndex.putIfAbsent(mojibakeBackslash, mainEntry);
                    }
                } catch (Exception ignored) {}
            }
        }

        if (mojibakeCount > 0) {
            logger.debug("Added {} mojibake path mappings", mojibakeCount);
        }

        indexBuilt = true;
        long elapsed = System.currentTimeMillis() - startTime;
        logger.debug("File index built in {}ms", elapsed);
    }

    public boolean isSearchEnabled() {
        return enableSearch;
    }

    public CacheEntry getFileWithMetadata(String filePath) {
        String cacheKey = filePath.toLowerCase();
        CacheEntry cached = fileCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        byte[] content = getFile(filePath);
        if (content != null) {
            // getFile already puts it in cache, so we can retrieve it again
            // or we could refactor getFile to return CacheEntry
            return fileCache.get(cacheKey);
        }
        return null;
    }

    public byte[] getFile(String filePath) {
        String cacheKey = filePath.toLowerCase();
        CacheEntry cached = fileCache.get(cacheKey);
        if (cached != null) {
            return cached.data();
        }

        Path localPath;
        String lowerPath = filePath.toLowerCase().replace('\\', '/');

        if (lowerPath.startsWith("bgm/")) {
            String bgmFile = filePath.substring(4);
            localPath = Paths.get(rootPath, BGM_PATH, bgmFile);
        } else if (lowerPath.startsWith("data/")) {
            localPath = Paths.get(rootPath, DATA_PATH, filePath.substring(5));
        } else if (lowerPath.startsWith("ai/")) {
            localPath = Paths.get(rootPath, AI_PATH, filePath.substring(3));
        } else if (lowerPath.startsWith("system/")) {
            localPath = Paths.get(rootPath, SYSTEM_PATH, filePath.substring(7));
        } else {
            localPath = Paths.get(rootPath, RESOURCES_PATH, filePath);
        }

        if (Files.exists(localPath)) {
            try {
                byte[] content = Files.readAllBytes(localPath);
                fileCache.set(cacheKey, content);
                return content;
            } catch (IOException e) {
                logger.error("Error reading local file: {}", e.getMessage());
            }
        }

        // Try external path mapping first
        String mappedPath = externalPathMapping.get(filePath);
        if (mappedPath == null) mappedPath = externalPathMapping.get(filePath.replace('/', '\\'));
        if (mappedPath == null) mappedPath = externalPathMapping.get(filePath.replace('\\', '/'));
        
        // Try normalized versions if still null
        if (mappedPath == null) {
            String norm = filePath.replace('\\', '/').toLowerCase();
            mappedPath = externalPathMapping.get(norm);
            if (mappedPath == null) {
                mappedPath = externalPathMapping.get(filePath.replace('/', '\\').toLowerCase());
            }
        }
        
        if (mappedPath == null) mappedPath = externalPathMapping.get(filePath.toLowerCase());

        String lookupPath = (mappedPath != null) ? mappedPath : filePath;

        String normalizedPath = lookupPath.toLowerCase().replace('\\', '/');
        String normalizedBackslash = lookupPath.toLowerCase().replace('/', '\\');

        FileIndexEntry entry = fileIndex.get(normalizedPath);
        if (entry == null) entry = fileIndex.get(normalizedBackslash);

        if (entry == null) {
            String decoded = decodeMojibake(lookupPath);
            if (!decoded.equals(lookupPath)) {
                entry = fileIndex.get(decoded.toLowerCase().replace('\\', '/'));
                if (entry == null) entry = fileIndex.get(decoded.toLowerCase().replace('/', '\\'));
            }
        }

        if (entry != null) {
            GRFService grf = grfs.get(entry.grfIndex);
            byte[] content = grf.getFile(entry.originalPath);
            if (content != null) {
                fileCache.set(cacheKey, content);
                if (autoExtract) {
                    extractFile(localPath, content);
                }
                return content;
            }
        }

        logMissingFile(filePath, lookupPath.replace('/', '\\'), mappedPath);
        return null;
    }

    private void loadPathMapping() {
        Path resourcesPath = Paths.get(rootPath, "resources");
        Path mappingPath = resourcesPath.resolve("path-mapping.json");

        if (!Files.exists(mappingPath) && usePathMappings) {
            logger.info("Path mapping file missing, attempting to generate it...");
            try {
                Path dataPath = Paths.get(rootPath, RESOURCES_PATH);
                Path dataIniPath = dataPath.resolve(dataIniName);
                if (Files.exists(dataIniPath)) {
                    Files.createDirectories(resourcesPath);
                    String dataIniContent = Files.readString(dataIniPath, Charset.forName("CP949"));
                    PathMappingTool.generateMapping(dataIniContent, dataPath, mappingPath);
                } else {
                    logger.warn("Cannot generate path mapping: DATA.INI not found at {}", dataIniPath);
                }
            } catch (Exception e) {
                logger.error("Failed to auto-generate path mapping: {}", e.getMessage());
            }
        }

        if (Files.exists(mappingPath)) {
            try {
                JsonNode root = objectMapper.readTree(mappingPath.toFile());
                if (root != null && root.has("paths")) {
                    JsonNode pathsNode = root.get("paths");
                    Map<String, String> mappings = objectMapper.convertValue(pathsNode, new TypeReference<Map<String, String>>() {});
                    if (mappings != null) {
                        externalPathMapping.putAll(mappings);
                        logger.info("Loaded {} path mappings from {}", mappings.size(), mappingPath);
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to load path-mapping.json: {}", e.getMessage());
            }
        }
    }


    private String decodeMojibake(String str) {
        try {
            byte[] latin1Bytes = str.getBytes(StandardCharsets.ISO_8859_1);
            return new String(latin1Bytes, Charset.forName("CP949"));
        } catch (Exception e) {
            return str;
        }
    }

    private void extractFile(Path localPath, byte[] content) {
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(localPath.getParent());
                Files.write(localPath, content);
            } catch (IOException e) {
                logger.error("Failed to extract file: {}", e.getMessage());
            }
        });
    }

    private void logMissingFile(String requestedPath, String grfPath, String mappedPath) {
        if (!missingFilesSet.add(requestedPath)) return;

        MissingFileLogEntry entry = new MissingFileLogEntry(Instant.now(), requestedPath, grfPath, mappedPath);
        synchronized (missingFilesLock) {
            missingFiles.add(entry);
            if (missingFiles.size() > 1000) {
                missingFiles.pollFirst();
            }
        }

        missingFileLogger.info(requestedPath);
        checkNotification();
    }

    private void checkNotification() {
        long now = System.currentTimeMillis();
        if (now - lastNotificationTime < NOTIFICATION_COOLDOWN) return;
        
        int count;
        synchronized (missingFilesLock) {
            count = missingFiles.size();
        }
        if (count < 10) return;

        lastNotificationTime = now;
        logger.warn("MISSING FILES ALERT: {} files not found.", count);
    }

    private Map<String, List<String>> parseIni(String data) {
        Map<String, List<String>> value = new HashMap<>();
        String[] lines = data.split("[\\r\\n]+");
        String section = null;

        Pattern sectionPattern = Pattern.compile("^\\s*\\[\\s*([^\\]]*)\\s*\\]\\s*$");
        Pattern paramPattern = Pattern.compile("^\\s*([\\w\\.\\-\\_]+)\\s*=\\s*(.*?)\\s*$");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith(";")) continue;

            var sectionMatch = sectionPattern.matcher(line);
            if (sectionMatch.matches()) {
                section = sectionMatch.group(1).toLowerCase();
                value.putIfAbsent(section, new ArrayList<>());
                continue;
            }

            var paramMatch = paramPattern.matcher(line);
            if (paramMatch.matches()) {
                String val = paramMatch.group(2);
                if (section != null) {
                    try {
                        int index = Integer.parseInt(paramMatch.group(1));
                        List<String> list = value.get(section);
                        while (list.size() <= index) list.add(null);
                        list.set(index, val);
                    } catch (NumberFormatException e) {
                        // ignore or handle non-numeric keys
                    }
                }
            }
        }
        return value;
    }

    public List<String> listFiles() {
        if (indexBuilt) {
            return uniqueEntries.stream()
                    .map(e -> e.originalPath)
                    .collect(Collectors.toList());
        }
        return grfs.stream()
                .flatMap(grf -> grf.listFiles().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<String> search(String regex) {
        if (!enableSearch) return Collections.emptyList();
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        return listFiles().stream()
                .filter(f -> pattern.matcher(f).find())
                .collect(Collectors.toList());
    }

    public Map<String, Object> getIndexStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFiles", fileIndex.size());
        stats.put("grfCount", grfs.size());
        stats.put("indexBuilt", indexBuilt);
        return stats;
    }

    public int warmCache(List<Pattern> patterns, int limit) {
        List<Pattern> defaultPatterns = List.of(
                Pattern.compile("data/texture/À¯ÀúÀÎÅÍÆäÀÌ½º", Pattern.CASE_INSENSITIVE),
                Pattern.compile("data/texture/userinterface", Pattern.CASE_INSENSITIVE),
                Pattern.compile("loading/", Pattern.CASE_INSENSITIVE),
                Pattern.compile("cardbmp/", Pattern.CASE_INSENSITIVE),
                Pattern.compile("prontera\\.gat$", Pattern.CASE_INSENSITIVE),
                Pattern.compile("prontera\\.gnd$", Pattern.CASE_INSENSITIVE),
                Pattern.compile("prontera\\.rsw$", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\.gat$", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\.rsw$", Pattern.CASE_INSENSITIVE),
                Pattern.compile("data/sprite/ÀÎ°£Á·", Pattern.CASE_INSENSITIVE),
                Pattern.compile("data/sprite/인간족", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\.pal$", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\.lub$", Pattern.CASE_INSENSITIVE)
        );

        List<Pattern> patternsToUse = (patterns == null || patterns.isEmpty()) ? defaultPatterns : patterns;
        int warmed = 0;
        long startTime = System.currentTimeMillis();

        List<FileIndexEntry> entriesToProcess = indexBuilt ? uniqueEntries : new ArrayList<>(fileIndex.values());

        for (FileIndexEntry entry : entriesToProcess) {
            if (warmed >= limit) break;

            for (Pattern pattern : patternsToUse) {
                if (pattern.matcher(entry.originalPath).find()) {
                    GRFService grf = grfs.get(entry.grfIndex);
                    byte[] content = grf.getFile(entry.originalPath);
                    if (content != null) {
                        fileCache.set(entry.originalPath.toLowerCase(), content);
                        warmed++;
                    }
                    break;
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("Cache warmed with {} files in {}ms", warmed, elapsed);
        return warmed;
    }
}
