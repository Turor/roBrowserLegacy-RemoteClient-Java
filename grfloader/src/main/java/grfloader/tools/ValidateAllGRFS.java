package turoran.robrowser.grfloader.tools;

import turoran.robrowser.grfloader.loader.*;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * ValidateAllGRFS
 *
 * Validates ALL GRF files in a folder:
 * - Detects names with U+FFFD and C1 controls (U+0080-U+009F)
 * - Does encoding round-trip (RAW and after heuristic repairs)
 * - Tests actual file reading (better sampling)
 * - Generates detailed report
 * <a href="https://github.com/FranciscoWallison/grf-loader/blob/main/tools/validate-all-grfs.mjs">Validate all GRFs</a>
 */
public class ValidateAllGRFS {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java turoran.grfloader.tools.ValidateAllGRFS <folder> [encoding=auto] [--read=100] [--examples=20]");
            System.err.println("Ex:");
            System.err.println("  java turoran.grfloader.tools.ValidateAllGRFS D:\\GRFs auto");
            System.err.println("  java turoran.grfloader.tools.ValidateAllGRFS ./resources cp949 --read=300");
            System.exit(1);
        }

        String folderPath = args[0];
        String encodingRequested = args.length > 1 ? args[1].toLowerCase() : "auto";
        int maxReadTests = 100;
        int maxExamples = 20;

        for (String arg : args) {
            if (arg.startsWith("--read=")) {
                maxReadTests = Math.max(0, Integer.parseInt(arg.split("=")[1]));
            } else if (arg.startsWith("--examples=")) {
                maxExamples = Math.max(1, Integer.parseInt(arg.split("=")[1]));
            }
        }

        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            System.err.println("Invalid folder: " + folderPath);
            System.exit(1);
        }

        System.out.println("GRF Validation Tool (Java)");
        System.out.println("Folder:   " + folder.getAbsolutePath());
        System.out.println("Encoding: " + encodingRequested);
        System.out.println("ReadTests per GRF: " + maxReadTests);
        System.out.println("Examples per bucket: " + maxExamples);
        System.out.println("");

        List<File> grfFiles = findGrfFiles(folder);
        if (grfFiles.isEmpty()) {
            System.err.println("No GRF files found!");
            System.exit(1);
        }
        System.out.println("[1] Scanning for GRF files...");
        System.out.println("    Found " + grfFiles.size() + " GRF file(s)\n");

        Report report = new Report();
        report.meta.folder = folder.getAbsolutePath();
        report.meta.encodingRequested = encodingRequested;
        report.meta.startedAt = new Date().toString();
        report.meta.grfCount = grfFiles.size();

        System.out.println("[2] Validating GRF files...\n");

        for (int i = 0; i < grfFiles.size(); i++) {
            File grfFile = grfFiles.get(i);
            System.out.println("[" + (i + 1) + "/" + grfFiles.size() + "] " + grfFile.getName());

            GrfResult result = validateGrf(grfFile, encodingRequested, maxReadTests, maxExamples);
            report.grfs.add(result);

            if (result.success) {
                report.summary.successfulLoads++;
                report.summary.totalFiles += result.validation.totalFiles;
                report.summary.totalBadUfffd += result.validation.badUfffd;
                report.summary.totalBadC1Control += result.validation.badC1Control;
                report.summary.totalRoundTripFailRaw += result.validation.roundTripFailRaw;
                report.summary.totalRoundTripRepairable += result.validation.roundTripRepairable;
                report.summary.totalRoundTripFailFinal += result.validation.roundTripFailFinal;
                report.summary.totalReadTestsPassed += result.validation.readTestsPassed;
                report.summary.totalReadTestsFailed += result.validation.readTestsFailed;

                System.out.printf("    \u2705 Loaded: %d files, %dms, detected=%s, validateAs=%s\n",
                        result.validation.totalFiles, result.loadTimeMs, result.detectedEncoding, result.encodingValidate);
                System.out.printf("       BadNames: U+FFFD=%d, C1=%d\n",
                        result.validation.badUfffd, result.validation.badC1Control);
                System.out.printf("       RoundTrip: rawFail=%d, repairable=%d, finalFail=%d\n",
                        result.validation.roundTripFailRaw, result.validation.roundTripRepairable, result.validation.roundTripFailFinal);
                System.out.printf("       Read tests: %d passed, %d failed\n",
                        result.validation.readTestsPassed, result.validation.readTestsFailed);
            } else {
                report.summary.failedLoads++;
                System.out.println("    \u274C Failed: " + result.error);
            }
            System.out.println("");
        }

        report.meta.finishedAt = new Date().toString();
        
        System.out.println("SUMMARY");
        System.out.printf("GRFs loaded:              %d/%d\n", report.summary.successfulLoads, report.summary.totalGrfs = report.meta.grfCount);
        System.out.printf("Total files:              %,d\n", report.summary.totalFiles);
        System.out.printf("Bad U+FFFD:               %,d\n", report.summary.totalBadUfffd);
        System.out.printf("Bad C1 Control:           %,d\n", report.summary.totalBadC1Control);
        System.out.printf("Round-trip fails (RAW):    %,d\n", report.summary.totalRoundTripFailRaw);
        System.out.printf("Round-trip repairable:     %,d\n", report.summary.totalRoundTripRepairable);
        System.out.printf("Round-trip fails (FINAL):  %,d\n", report.summary.totalRoundTripFailFinal);
        System.out.printf("Read tests passed:         %,d\n", report.summary.totalReadTestsPassed);
        System.out.printf("Read tests failed:         %,d\n", report.summary.totalReadTestsFailed);
        System.out.println("");

        double healthPct = report.summary.totalFiles > 0
                ? ((double) (report.summary.totalFiles - (report.summary.totalBadUfffd + report.summary.totalBadC1Control)) / report.summary.totalFiles * 100)
                : 0;
        System.out.printf("Encoding Health (no U+FFFD/C1): %.4f%%\n", healthPct);

        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        String outName = "grf-validation-" + stamp + ".json";
        
        // Output as simple text representation since Jackson is not available
        System.out.println("\n(JSON report generation skipped as Jackson is not in classpath)");
        System.out.println("Report details would be saved to: " + outName);
        
        // Print some examples if they exist
        for (GrfResult res : report.grfs) {
            if (res.success && !res.examples.badUfffd.isEmpty()) {
                System.out.println("Examples of bad U+FFFD in " + res.filename + ": " + res.examples.badUfffd.get(0));
            }
        }

        if (report.summary.failedLoads > 0) System.exit(2);
        if (report.summary.totalRoundTripFailFinal > 0 || report.summary.totalReadTestsFailed > 0) System.exit(1);
        System.exit(0);
    }

    private static List<File> findGrfFiles(File folder) {
        List<File> grfs = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    grfs.addAll(findGrfFiles(f));
                } else if (f.getName().toLowerCase().endsWith(".grf")) {
                    grfs.add(f);
                }
            }
        }
        return grfs;
    }

    private static GrfResult validateGrf(File file, String encodingRequested, int maxReadTests, int maxExamples) {
        GrfResult result = new GrfResult();
        result.path = file.getAbsolutePath();
        result.filename = file.getName();
        result.size = file.length();
        result.sizeFormatted = formatBytes(result.size);
        result.encodingRequested = encodingRequested;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            GRFNodeOptions options = new GRFNodeOptions();
            options.filenameEncoding = FilenameEncoding.fromString(encodingRequested);
            GRFNode grf = new GRFNode(raf, options);

            long t0 = System.currentTimeMillis();
            grf.load();
            result.loadTimeMs = System.currentTimeMillis() - t0;

            GrfStats stats = grf.getStats();
            result.stats = stats;
            result.detectedEncoding = stats.detectedEncoding.getValue();
            result.encodingValidate = normalizeEncodingForValidation(result.detectedEncoding);
            Charset validationCharset = Charset.forName(result.encodingValidate);

            List<String> allFiles = grf.listFiles();
            result.validation.totalFiles = allFiles.size();
            List<String> suspicious = new ArrayList<>();

            for (int i = 0; i < allFiles.size(); i++) {
                String name = allFiles.get(i);

                boolean hasUfffd = name.contains("\uFFFD");
                boolean hasC1 = Decoder.countC1ControlChars(name) > 0;

                if (hasUfffd) {
                    result.validation.badUfffd++;
                    suspicious.add(name);
                    if (result.examples.badUfffd.size() < maxExamples) result.examples.badUfffd.add(name);
                }
                if (hasC1) {
                    result.validation.badC1Control++;
                    suspicious.add(name);
                    if (result.examples.badC1Control.size() < maxExamples) result.examples.badC1Control.add(name);
                }

                boolean rawOk = roundTripOk(name, validationCharset);
                if (!rawOk) {
                    result.validation.roundTripFailRaw++;
                    if (result.examples.roundTripFailRaw.size() < maxExamples) {
                        Map<String, String> ex = new HashMap<>();
                        ex.put("original", name);
                        ex.put("roundTrip", doRoundTrip(name, validationCharset));
                        result.examples.roundTripFailRaw.add(ex);
                    }
                }

                String repaired = Decoder.repairFilename(name, validationCharset);
                boolean repairedOk = roundTripOk(repaired, validationCharset);

                if (!rawOk && repairedOk) {
                    result.validation.roundTripRepairable++;
                    if (result.examples.roundTripRepairable.size() < maxExamples) {
                        Map<String, String> ex = new HashMap<>();
                        ex.put("original", name);
                        ex.put("repaired", repaired);
                        result.examples.roundTripRepairable.add(ex);
                    }
                }

                if (!repairedOk) {
                    result.validation.roundTripFailFinal++;
                    if (result.examples.roundTripFailFinal.size() < maxExamples) {
                        Map<String, String> ex = new HashMap<>();
                        ex.put("original", name);
                        ex.put("repaired", repaired);
                        ex.put("roundTrip", doRoundTrip(repaired, validationCharset));
                        result.examples.roundTripFailFinal.add(ex);
                    }
                }
            }

            List<String> filesToTest = pickReadTestFiles(allFiles, suspicious, maxReadTests);
            for (String name : filesToTest) {
                try {
                    FileResult fr = grf.getFile(name);
                    if (fr != null && fr.data != null && fr.data.length > 0) {
                        result.validation.readTestsPassed++;
                    } else {
                        result.validation.readTestsFailed++;
                        if (result.examples.readFailed.size() < maxExamples) {
                            Map<String, String> ex = new HashMap<>();
                            ex.put("filename", name);
                            ex.put("error", fr != null ? fr.error : "null result");
                            result.examples.readFailed.add(ex);
                        }
                    }
                } catch (Exception e) {
                    result.validation.readTestsFailed++;
                    if (result.examples.readFailed.size() < maxExamples) {
                        Map<String, String> ex = new HashMap<>();
                        ex.put("filename", name);
                        ex.put("error", e.getMessage());
                        result.examples.readFailed.add(ex);
                    }
                }
            }

            result.success = true;
        } catch (Exception e) {
            result.success = false;
            result.error = e.getMessage();
        }

        return result;
    }

    private static String normalizeEncodingForValidation(String enc) {
        String e = enc != null ? enc.toLowerCase() : "cp949";
        if (e.equals("euc-kr") || e.equals("auto") || e.contains("949")) return "windows-949";
        return e;
    }

    private static boolean roundTripOk(String s, Charset charset) {
        byte[] b = s.getBytes(charset);
        String back = new String(b, charset);
        return s.equals(back);
    }

    private static String doRoundTrip(String s, Charset charset) {
        byte[] b = s.getBytes(charset);
        return new String(b, charset);
    }

    private static List<String> pickReadTestFiles(List<String> all, List<String> suspicious, int maxN) {
        if (maxN <= 0 || all.isEmpty()) return Collections.emptyList();
        Set<String> out = new LinkedHashSet<>();
        int wantSus = Math.min((int) (maxN * 0.4), suspicious.size());
        for (int i = 0; i < wantSus; i++) out.add(suspicious.get(i));

        if (out.size() < maxN) {
            int third = Math.max(1, (maxN - out.size()) / 3);
            for (int i = 0; i < Math.min(third, all.size()); i++) out.add(all.get(i));
            int mid = all.size() / 2;
            for (int i = mid; i < Math.min(mid + third, all.size()); i++) out.add(all.get(i));
            Random rng = new Random();
            while (out.size() < maxN && out.size() < all.size()) {
                out.add(all.get(rng.nextInt(all.size())));
            }
        }
        List<String> list = new ArrayList<>(out);
        return list.size() > maxN ? list.subList(0, maxN) : list;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    // --- Report POJOs ---
    public static class Report {
        public Meta meta = new Meta();
        public Summary summary = new Summary();
        public List<GrfResult> grfs = new ArrayList<>();
    }

    public static class Meta {
        public String folder;
        public String encodingRequested;
        public String startedAt;
        public String finishedAt;
        public int grfCount;
    }

    public static class Summary {
        public int totalGrfs;
        public int successfulLoads;
        public int failedLoads;
        public int totalFiles;
        public int totalBadUfffd;
        public int totalBadC1Control;
        public int totalRoundTripFailRaw;
        public int totalRoundTripRepairable;
        public int totalRoundTripFailFinal;
        public int totalReadTestsPassed;
        public int totalReadTestsFailed;
    }

    public static class GrfResult {
        public String path;
        public String filename;
        public long size;
        public String sizeFormatted;
        public String encodingRequested;
        public String detectedEncoding;
        public String encodingValidate;
        public long loadTimeMs;
        public boolean success;
        public String error;
        public GrfStats stats;
        public Validation validation = new Validation();
        public Examples examples = new Examples();
    }

    public static class Validation {
        public int totalFiles;
        public int badUfffd;
        public int badC1Control;
        public int roundTripFailRaw;
        public int roundTripRepairable;
        public int roundTripFailFinal;
        public int readTestsPassed;
        public int readTestsFailed;
    }

    public static class Examples {
        public List<String> badUfffd = new ArrayList<>();
        public List<String> badC1Control = new ArrayList<>();
        public List<Map<String, String>> roundTripFailRaw = new ArrayList<>();
        public List<Map<String, String>> roundTripRepairable = new ArrayList<>();
        public List<Map<String, String>> roundTripFailFinal = new ArrayList<>();
        public List<Map<String, String>> readFailed = new ArrayList<>();
    }
}
