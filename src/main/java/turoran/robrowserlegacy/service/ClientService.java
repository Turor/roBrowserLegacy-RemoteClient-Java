package turoran.robrowserlegacy.service;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Value;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turoran.robrowserlegacy.model.CacheEntry;
import turoran.robrowserlegacy.model.MissingFileLogEntry;
import turoran.robrowserlegacy.util.StartupValidator;

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

    private final LRUCacheService fileCache;
    private final StartupValidator startupValidator;
    private final BeanContext beanContext;

    @Value("${client.respath:.}")
    private String resPath;

    @Value("${client.dataini:DATA.INI}")
    private String dataIniName;

    @Value("${client.autoextract:false}")
    private boolean autoExtract;

    @Value("${client.enablesearch:true}")
    private boolean enableSearch;

    private List<GRFService> grfs = new ArrayList<>();
    private final Map<String, FileIndexEntry> fileIndex = new ConcurrentHashMap<>();
    private boolean indexBuilt = false;
    
    private final Set<String> missingFilesSet = ConcurrentHashMap.newKeySet();
    private final List<MissingFileLogEntry> missingFiles = new CopyOnWriteArrayList<>();
    private long lastNotificationTime = 0;
    private static final long NOTIFICATION_COOLDOWN = 60000;

    private Map<String, String> externalPathMapping = new HashMap<>();

    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private record FileIndexEntry(int grfIndex, String originalPath, String mappedFrom) {}

    public ClientService(LRUCacheService fileCache, StartupValidator startupValidator, BeanContext beanContext) {
        this.fileCache = fileCache;
        this.startupValidator = startupValidator;
        this.beanContext = beanContext;
    }

    @PostConstruct
    public void init() {
        startupValidator.printReport(startupValidator.validateAll(false));

        loadPathMapping();

        long startTime = System.currentTimeMillis();
        Path dataIniPath = Paths.get(resPath, dataIniName);

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
                String fullPath = Paths.get(resPath, grfName).toString();
                GRFService grf = beanContext.createBean(GRFService.class, fullPath);
                grf.load();
                if (grf.isLoaded()) {
                    grfs.add(grf);
                }
            }

            buildFileIndex();

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Client initialized in {}ms ({} files indexed)", elapsed, fileIndex.size());

            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.scheduleWithFixedDelay(this::flushLogQueue, 1, 1, TimeUnit.SECONDS);
            }

        } catch (IOException e) {
            logger.error("Failed to read DATA.INI", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        for (GRFService grf : grfs) {
            grf.close();
        }
        scheduler.shutdown();
        flushLogQueue();
    }

    private void buildFileIndex() {
        long startTime = System.currentTimeMillis();
        fileIndex.clear();
        int mojibakeCount = 0;

        Charset cp949 = Charset.forName("CP949");
        Charset latin1 = StandardCharsets.ISO_8859_1;

        for (int i = 0; i < grfs.size(); i++) {
            GRFService grf = grfs.get(i);
            List<String> files = grf.listFiles();
            for (String file : files) {
                String normalized = file.toLowerCase().replace('\\', '/');
                fileIndex.putIfAbsent(normalized, new FileIndexEntry(i, file, null));

                String normalizedBackslash = file.toLowerCase().replace('/', '\\');
                fileIndex.putIfAbsent(normalizedBackslash, new FileIndexEntry(i, file, null));

                try {
                    byte[] cp949Bytes = file.getBytes(cp949);
                    String mojibakePath = new String(cp949Bytes, latin1);
                    if (!mojibakePath.equals(file)) {
                        String normalizedMojibake = mojibakePath.toLowerCase().replace('\\', '/');
                        if (fileIndex.putIfAbsent(normalizedMojibake, new FileIndexEntry(i, file, null)) == null) {
                            mojibakeCount++;
                        }
                        String mojibakeBackslash = mojibakePath.toLowerCase().replace('/', '\\');
                        fileIndex.putIfAbsent(mojibakeBackslash, new FileIndexEntry(i, file, null));
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

        Path localPath = Paths.get(resPath, filePath);
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
        Path mappingPath = Paths.get("path-mapping.json");
        if (!Files.exists(mappingPath)) {
            mappingPath = Paths.get(resPath, "path-mapping.json");
        }

        if (Files.exists(mappingPath)) {
            try {
                String content = Files.readString(mappingPath);
                // Very simple manual parsing for "paths": { ... }
                int pathsIndex = content.indexOf("\"paths\":");
                if (pathsIndex != -1) {
                    int startBrace = content.indexOf('{', pathsIndex);
                    int endBrace = findMatchingBrace(content, startBrace);
                    if (startBrace != -1 && endBrace != -1) {
                        String pathsJson = content.substring(startBrace + 1, endBrace);
                        String[] entries = pathsJson.split(",\\s*\\r?\\n");
                        for (String entry : entries) {
                            int colonIndex = entry.indexOf("\": \"");
                            if (colonIndex != -1) {
                                String keyPart = entry.substring(0, colonIndex).trim();
                                String valPart = entry.substring(colonIndex + 4).trim();
                                
                                String key = keyPart.replace("\"", "").replace("\\\\", "\\");
                                String value = valPart.replace("\"", "").replace(",", "").replace("\\\\", "\\").trim();
                                if (value.endsWith("\"")) value = value.substring(0, value.length() - 1);
                                
                                externalPathMapping.put(key, value);
                            }
                        }
                        logger.info("Loaded {} path mappings from {}", externalPathMapping.size(), mappingPath);
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to load path-mapping.json: {}", e.getMessage());
            }
        }
    }

    private int findMatchingBrace(String s, int start) {
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) == '{') depth++;
            else if (s.charAt(i) == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
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
        missingFiles.add(entry);
        if (missingFiles.size() > 1000) {
            missingFiles.remove(0);
        }

        logQueue.offer(entry.timestamp().toString() + " - " + requestedPath + "\n");
        checkNotification();
    }

    private void flushLogQueue() {
        List<String> entries = new ArrayList<>();
        logQueue.drainTo(entries);
        if (entries.isEmpty()) return;

        try {
            Path logDir = Paths.get("logs");
            Files.createDirectories(logDir);
            Files.write(logDir.resolve("missing-files.log"), entries, StandardCharsets.UTF_8, 
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.error("Failed to write missing file log: {}", e.getMessage());
        }
    }

    private void checkNotification() {
        long now = System.currentTimeMillis();
        if (now - lastNotificationTime < NOTIFICATION_COOLDOWN) return;
        if (missingFiles.size() < 10) return;

        lastNotificationTime = now;
        logger.warn("MISSING FILES ALERT: {} files not found.", missingFiles.size());
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
            return fileIndex.values().stream()
                    .map(e -> e.originalPath)
                    .distinct()
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

        for (FileIndexEntry entry : fileIndex.values()) {
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
