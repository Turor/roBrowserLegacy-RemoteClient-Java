package turoran.grfloader.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import turoran.grfloader.loader.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * PathMappingTool
 * Generates a path mapping file that maps mojibake/C1 paths to their corrected versions.
 * This mapping can be used by the server to resolve file lookups.
 * Usage:
 *  java -cp ... turoran.grfloader.tools.PathMappingTool [--output=path-mapping.json]
 */
@Slf4j
public class PathMappingTool {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) {
        try {
            execute(args);
        } catch (Exception e) {
            log.error("Fatal error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    public static void execute(String[] args) throws IOException {
        String outputPath = "path-mapping.json";
        for (String arg : args) {
            if (arg.startsWith("--output=")) {
                outputPath = arg.split("=")[1];
            }
        }

        log.info("GRF Encoding Converter (Java)");
        log.info("Output: {}", outputPath);

        generateMapping(outputPath);
    }

    public static void generateMapping(String outputPath) throws IOException {
        String resPath = System.getProperty("resources.path", "resources");
        Path resourcesPath = Paths.get(resPath);
        Path dataIniPath = resourcesPath.resolve("DATA.INI");

        if (!Files.exists(dataIniPath)) {
            // Try current directory if resources/DATA.INI not found
            resourcesPath = Paths.get(".");
            dataIniPath = resourcesPath.resolve("DATA.INI");
            if (!Files.exists(dataIniPath)) {
                log.error("ERROR: DATA.INI not found in {} or current directory!", resPath);
                return;
            }
        }

        String dataIniContent = Files.readString(dataIniPath);
        generateMapping(dataIniContent, resourcesPath, Paths.get(outputPath));
    }

    public static void generateMapping(String dataIniContent, Path resourcesPath, Path outputPath) throws IOException {
        List<String> grfFiles = parseDataINI(dataIniContent);

        if (grfFiles.isEmpty()) {
            log.error("ERROR: No GRF files found in DATA.INI!");
            return;
        }

        log.info("Found {} GRF file(s)", grfFiles.size());

        List<GRFStat> grfsStats = new ArrayList<>();
        Map<String, String> pathsMap = new LinkedHashMap<>();
        GRFSummary summary = new GRFSummary();

        for (String grfFile : grfFiles) {
            Path grfPath = resourcesPath.resolve(grfFile);

            if (!Files.exists(grfPath)) {
                log.info("SKIP: {} (not found)", grfFile);
                continue;
            }

            log.info("Processing: {}", grfFile);

            try (RandomAccessFile raf = new RandomAccessFile(grfPath.toFile(), "r")) {
                GRFNode grf = new GRFNode(raf);
                grf.load();

                List<String> allFiles = grf.listFiles();
                int grfMapped = 0;
                int grfMojibake = 0;
                int grfC1 = 0;

                for (String filename : allFiles) {
                    summary.addFiles(1);

                    boolean hasMojibake = Decoder.isMojibake(filename);
                    boolean hasC1 = Decoder.countC1ControlChars(filename) > 0;

                    if (hasMojibake || hasC1) {
                        String fixed = filename;

                        if (hasMojibake) {
                            fixed = Decoder.fixMojibake(filename);
                            grfMojibake++;
                        }

                        if (hasC1 && fixed.equals(filename)) {
                             fixed = Decoder.maybeFixLatin1Mojibake(filename, Decoder.CP949);
                             if (!fixed.equals(filename)) {
                                 grfC1++;
                             }
                        }

                        if (!fixed.equals(filename)) {
                            String koreanPath = fixed;
                            String grfPathStr = filename;

                            pathsMap.put(koreanPath, grfPathStr);

                            String normalizedKorean = koreanPath.replace('\\', '/').toLowerCase();
                            String normalizedGrf = grfPathStr.replace('\\', '/').toLowerCase();
                            pathsMap.put(normalizedKorean, normalizedGrf);
                            
                            String normalizedKoreanBS = koreanPath.replace('/', '\\').toLowerCase();
                            String normalizedGrfBS = grfPathStr.replace('/', '\\').toLowerCase();
                            if (!normalizedKoreanBS.equals(koreanPath)) {
                                pathsMap.put(normalizedKoreanBS, normalizedGrfBS);
                            }

                            grfMapped++;
                        }
                    }
                }

                GRFStat grfStat = GRFStat.builder()
                        .file(grfFile)
                        .totalFiles(allFiles.size())
                        .mapped(grfMapped)
                        .mojibake(grfMojibake)
                        .c1(grfC1)
                        .detectedEncoding(grf.getDetectedEncoding().toString())
                        .build();
                grfsStats.add(grfStat);

                summary.addMapped(grfMapped);
                summary.addMojibake(grfMojibake);
                summary.addC1(grfC1);

                System.out.printf("  Files: %,d | Mapped: %d | Mojibake: %d | C1: %d%n", 
                    allFiles.size(), grfMapped, grfMojibake, grfC1);

            } catch (Exception e) {
                log.error("  ERROR: {}", e.getMessage());
            }
        }

        PathMapping root = PathMapping.builder()
                .generatedAt(Instant.now())
                .grfs(grfsStats)
                .paths(pathsMap)
                .summary(summary)
                .build();

        objectMapper.writeValue(outputPath.toFile(), root);

        log.info("SUMMARY");
        log.info("Total files:      {}", String.format("%,d", summary.getTotalFiles()));
        log.info("Total mapped:     {}", String.format("%,d", summary.getTotalMapped()));
        log.info("Mojibake fixed:   {}", String.format("%,d", summary.getMojibakeFixed()));
        log.info("C1 fixed:         {}", String.format("%,d", summary.getC1Fixed()));
        log.info("");
        log.info("Mapping saved to: {}", outputPath.toAbsolutePath());
        log.info("");
    }


    private static List<String> parseDataINI(String content) {
        List<String> grfFiles = new ArrayList<>();
        String[] lines = content.split("[\\r\\n]+");
        boolean inDataSection = false;

        Pattern sectionPattern = Pattern.compile("^\\s*\\[\\s*([^\\]]*)\\s*\\]\\s*$");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue;

            var sectionMatch = sectionPattern.matcher(line);
            if (sectionMatch.matches()) {
                String section = sectionMatch.group(1).toLowerCase();
                inDataSection = section.equals("data");
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
}
