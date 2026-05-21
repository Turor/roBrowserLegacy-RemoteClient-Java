package turoran.grfloader.tools;

import turoran.grfloader.loader.Decoder;
import turoran.grfloader.loader.GRFNode;

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
 *
 * Generates a path mapping file that maps mojibake/C1 paths to their corrected versions.
 * This mapping can be used by the server to resolve file lookups.
 *
 * Usage:
 *  java -cp ... turoran.grfloader.tools.PathMappingTool [--output=path-mapping.json]
 */
public class PathMappingTool {

    public static void main(String[] args) {
        String outputPath = "path-mapping.json";
        for (String arg : args) {
            if (arg.startsWith("--output=")) {
                outputPath = arg.split("=")[1];
            }
        }

        System.out.println("GRF Encoding Converter (Java)");
        System.out.println("");
        System.out.println("Output: " + outputPath);
        System.out.println("");

        try {
            generateMapping(outputPath);
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
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
                System.err.println("ERROR: DATA.INI not found in " + resPath + " or current directory!");
                return;
            }
        }

        String dataIniContent = Files.readString(dataIniPath);
        List<String> grfFiles = parseDataINI(dataIniContent);

        if (grfFiles.isEmpty()) {
            System.err.println("ERROR: No GRF files found in DATA.INI!");
            return;
        }

        System.out.println("Found " + grfFiles.size() + " GRF file(s)\n");

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"generatedAt\": \"").append(Instant.now().toString()).append("\",\n");
        json.append("  \"grfs\": [\n");

        List<String> grfStatsJsons = new ArrayList<>();
        
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("totalFiles", 0);
        summary.put("totalMapped", 0);
        summary.put("mojibakeFixed", 0);
        summary.put("c1Fixed", 0);

        Map<String, String> pathsMap = new LinkedHashMap<>();

        for (int i = 0; i < grfFiles.size(); i++) {
            String grfFile = grfFiles.get(i);
            Path grfPath = resourcesPath.resolve(grfFile);

            if (!Files.exists(grfPath)) {
                System.out.println("SKIP: " + grfFile + " (not found)");
                continue;
            }

            System.out.println("Processing: " + grfFile);

            try (RandomAccessFile raf = new RandomAccessFile(grfPath.toFile(), "r")) {
                GRFNode grf = new GRFNode(raf);
                grf.load();

                List<String> allFiles = grf.listFiles();
                int grfMapped = 0;
                int grfMojibake = 0;
                int grfC1 = 0;

                for (String filename : allFiles) {
                    summary.put("totalFiles", summary.get("totalFiles") + 1);

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

                            pathsMap.put(escapeJson(koreanPath), escapeJson(grfPathStr));

                            String normalizedKorean = koreanPath.replace('\\', '/').toLowerCase();
                            String normalizedGrf = grfPathStr.replace('\\', '/').toLowerCase();
                            pathsMap.put(escapeJson(normalizedKorean), escapeJson(normalizedGrf));
                            
                            String normalizedKoreanBS = koreanPath.replace('/', '\\').toLowerCase();
                            String normalizedGrfBS = grfPathStr.replace('/', '\\').toLowerCase();
                            if (!normalizedKoreanBS.equals(koreanPath)) {
                                pathsMap.put(escapeJson(normalizedKoreanBS), escapeJson(normalizedGrfBS));
                            }

                            grfMapped++;
                        }
                    }
                }

                String grfStat = String.format(
                    "    {\n      \"file\": \"%s\",\n      \"totalFiles\": %d,\n      \"mapped\": %d,\n      \"mojibake\": %d,\n      \"c1\": %d,\n      \"detectedEncoding\": \"%s\"\n    }",
                    escapeJson(grfFile), allFiles.size(), grfMapped, grfMojibake, grfC1, grf.getDetectedEncoding().toString()
                );
                grfStatsJsons.add(grfStat);

                summary.put("totalMapped", summary.get("totalMapped") + grfMapped);
                summary.put("mojibakeFixed", summary.get("mojibakeFixed") + grfMojibake);
                summary.put("c1Fixed", summary.get("c1Fixed") + grfC1);

                System.out.printf("  Files: %,d | Mapped: %d | Mojibake: %d | C1: %d%n", 
                    allFiles.size(), grfMapped, grfMojibake, grfC1);

            } catch (Exception e) {
                System.out.println("  ERROR: " + e.getMessage());
            }
        }

        json.append(String.join(",\n", grfStatsJsons));
        json.append("\n  ],\n");
        json.append("  \"paths\": {\n");

        List<String> pathEntries = new ArrayList<>();
        for (Map.Entry<String, String> entry : pathsMap.entrySet()) {
            pathEntries.add("    \"" + entry.getKey() + "\": \"" + entry.getValue() + "\"");
        }
        json.append(String.join(",\n", pathEntries));
        json.append("\n  },\n");

        json.append("  \"summary\": {\n");
        json.append("    \"totalFiles\": ").append(summary.get("totalFiles")).append(",\n");
        json.append("    \"totalMapped\": ").append(summary.get("totalMapped")).append(",\n");
        json.append("    \"mojibakeFixed\": ").append(summary.get("mojibakeFixed")).append(",\n");
        json.append("    \"c1Fixed\": ").append(summary.get("c1Fixed")).append("\n");
        json.append("  }\n");
        json.append("}\n");

        Files.writeString(Paths.get(outputPath), json.toString());

        System.out.println("SUMMARY");
        System.out.println("Total files:      " + String.format("%,d", summary.get("totalFiles")));
        System.out.println("Total mapped:     " + String.format("%,d", summary.get("totalMapped")));
        System.out.println("Mojibake fixed:   " + String.format("%,d", summary.get("mojibakeFixed")));
        System.out.println("C1 fixed:         " + String.format("%,d", summary.get("c1Fixed")));
        System.out.println("");
        System.out.println("Mapping saved to: " + new File(outputPath).getAbsolutePath());
        System.out.println("");
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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
