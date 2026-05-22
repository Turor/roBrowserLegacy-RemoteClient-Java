package turoran.grfloader.tools;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turoran.grfloader.loader.*;

import java.io.RandomAccessFile;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * <a href="https://github.com/FranciscoWallison/grf-loader/blob/main/tools/test-grf-read.mjs">JS Verify GRF</a>
 */
@Slf4j
public class VerifyGRF {

    public static void main(String[] args) {
        try {
            execute(args);
        } catch (Exception e) {
            log.error("Fatal error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    public static boolean execute(String[] args) throws Exception {
        if (args.length < 1) {
            log.error("Uso: java turoran.grfloader.tools.VerifyGRF <grfPath> [encoding=auto] [count=100]");
            return false;
        }

        String grfPath = args[0];
        String encodingStr = args.length > 1 ? args[1] : "auto";
        int testCount = args.length > 2 ? Integer.parseInt(args[2]) : 100;

        log.info("GRF Read Test (Java)");
        log.info("File: {}", Paths.get(grfPath).toAbsolutePath());
        log.info("Encoding: {}", encodingStr);
        log.info("Test count: {}", testCount);

        FilenameEncoding encoding = FilenameEncoding.fromString(encodingStr);

        try (RandomAccessFile fd = new RandomAccessFile(grfPath, "r")) {
            log.info("[1] Loading GRF...");

            GRFNodeOptions options = new GRFNodeOptions();
            options.filenameEncoding = encoding;

            GRFNode grf = new GRFNode(fd, options);

            long loadStart = System.currentTimeMillis();
            grf.load();
            long loadTime = System.currentTimeMillis() - loadStart;

            GrfStats stats = grf.getStats();
            log.info("    Loaded in {}ms", loadTime);
            log.info("    Files: {}", stats.fileCount);
            log.info("    Detected encoding: {}", stats.detectedEncoding);
            log.info("    Bad names (U+FFFD/C1): {}", stats.badNameCount);
            log.info("    Collisions: {}", stats.collisionCount);

            // Get files to test
            List<String> allFiles = grf.listFiles();
            List<String> filesToTest = new ArrayList<>();

            // Get a variety of files
            List<String> extensions = grf.listExtensions();
            log.info("    Extensions found: {}", extensions.stream().limit(20).reduce((a, b) -> a + ", " + b).orElse(""));
            if (extensions.size() > 20) log.info("...");

            // Sample files from different extensions
            int extLimit = Math.min(10, extensions.size());
            for (int i = 0; i < extLimit; i++) {
                String ext = extensions.get(i);
                List<String> filesWithExt = grf.getFilesByExtension(ext);
                int take = (int) Math.ceil((double) testCount / 10);
                for (int j = 0; j < Math.min(take, filesWithExt.size()); j++) {
                    filesToTest.add(filesWithExt.get(j));
                }
            }

            // Add more random files if needed
            if (filesToTest.size() < testCount && !allFiles.isEmpty()) {
                int remaining = testCount - filesToTest.size();
                int step = Math.max(1, allFiles.size() / remaining);
                for (int i = 0; i < allFiles.size() && filesToTest.size() < testCount; i += step) {
                    String filename = allFiles.get(i);
                    if (!filesToTest.contains(filename)) {
                        filesToTest.add(filename);
                    }
                }
            }

            log.info("[2] Testing {} file reads...", filesToTest.size());

            int passed = 0;
            int failed = 0;
            List<String[]> failures = new ArrayList<>();

            for (int i = 0; i < filesToTest.size(); i++) {
                String filename = filesToTest.get(i);

                if (i % 20 == 0 || i == filesToTest.size() - 1) {
                    double pct = ((double) (i + 1) / filesToTest.size() * 100);
                    log.info("    Progress: {}/{} ({:.1f}%)", i + 1, filesToTest.size(), pct);
                }

                try {
                    FileResult result = grf.getFile(filename);

                    if (result.data != null && result.data.length > 0) {
                        passed++;
                    } else if (result.error != null) {
                        failed++;
                        if (failures.size() < 20) {
                            failures.add(new String[]{filename, result.error});
                        }
                    } else {
                        // Empty file - still counts as success
                        passed++;
                    }
                } catch (Exception e) {
                    failed++;
                    if (failures.size() < 20) {
                        failures.add(new String[]{filename, e.getMessage() != null ? e.getMessage() : e.toString()});
                    }
                }
            }

            log.info("RESULTS");
            log.info("Passed: {}", passed);
            log.info("Failed: {}", failed);
            log.info("Success rate: {:.2f}%", (double) passed / (passed + failed) * 100);

            if (!failures.isEmpty()) {
                log.info("Failures:");
                for (String[] f : failures) {
                    log.info("  - {}", f[0]);
                    log.info("    Error: {}", f[1]);
                }
            }

            // Test specific file lookup
            log.info("[3] Testing path resolution...");

            // Test case-insensitive lookup
            if (!allFiles.isEmpty()) {
                String testFile = allFiles.get(0);
                String upperCase = testFile.toUpperCase();
                String lowerCase = testFile.toLowerCase();

                ResolveResult resolved1 = grf.resolvePath(upperCase);
                ResolveResult resolved2 = grf.resolvePath(lowerCase);

                log.info("    Original: {}", testFile);
                log.info("    Upper lookup: {}", (resolved1 != null ? resolved1.status : "N/A"));
                log.info("    Lower lookup: {}", (resolved2 != null ? resolved2.status : "N/A"));
            }

            // Test hasFile
            if (!allFiles.isEmpty()) {
                boolean exists1 = grf.hasFile(allFiles.get(0));
                boolean exists2 = grf.hasFile("nonexistent/file/path.txt");
                log.info("    hasFile (exists): {}", exists1);
                log.info("    hasFile (not exists): {}", exists2);
            }

            if (failed == 0) {
                log.info("✅ All read tests passed!");
                return true;
            } else {
                log.warn("⚠️  {} read tests failed", failed);
                return false;
            }
        }
    }
}
