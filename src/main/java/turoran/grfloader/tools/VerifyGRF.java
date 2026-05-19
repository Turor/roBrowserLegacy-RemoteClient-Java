package turoran.grfloader.tools;

import turoran.grfloader.loader.*;

import java.io.RandomAccessFile;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * <a href="https://github.com/FranciscoWallison/grf-loader/blob/main/tools/test-grf-read.mjs">JS Verify GRF</a>
 */
public class VerifyGRF {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Uso: java turoran.grfloader.tools.VerifyGRF <grfPath> [encoding=auto] [count=100]");
            System.exit(1);
        }

        String grfPath = args[0];
        String encodingStr = args.length > 1 ? args[1] : "auto";
        int testCount = args.length > 2 ? Integer.parseInt(args[2]) : 100;

        System.out.println("=".repeat(70));
        System.out.println("GRF Read Test (Java)");
        System.out.println("=".repeat(70));
        System.out.println("File: " + Paths.get(grfPath).toAbsolutePath());
        System.out.println("Encoding: " + encodingStr);
        System.out.println("Test count: " + testCount);
        System.out.println();

        FilenameEncoding encoding = FilenameEncoding.fromString(encodingStr);

        try (RandomAccessFile fd = new RandomAccessFile(grfPath, "r")) {
            System.out.println("[1] Loading GRF...");
            
            GRFNodeOptions options = new GRFNodeOptions();
            options.filenameEncoding = encoding;
            
            GRFNode grf = new GRFNode(fd, options);

            long loadStart = System.currentTimeMillis();
            grf.load();
            long loadTime = System.currentTimeMillis() - loadStart;

            GrfStats stats = grf.getStats();
            System.out.println("    Loaded in " + loadTime + "ms");
            System.out.println("    Files: " + stats.fileCount);
            System.out.println("    Detected encoding: " + stats.detectedEncoding);
            System.out.println("    Bad names (U+FFFD/C1): " + stats.badNameCount);
            System.out.println("    Collisions: " + stats.collisionCount);
            System.out.println();

            // Get files to test
            List<String> allFiles = grf.listFiles();
            List<String> filesToTest = new ArrayList<>();

            // Get a variety of files
            List<String> extensions = grf.listExtensions();
            System.out.println("    Extensions found: " + extensions.stream().limit(20).reduce((a, b) -> a + ", " + b).orElse(""));
            if (extensions.size() > 20) System.out.println("...");
            System.out.println();

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

            System.out.println("[2] Testing " + filesToTest.size() + " file reads...");
            System.out.println();

            int passed = 0;
            int failed = 0;
            List<String[]> failures = new ArrayList<>();

            for (int i = 0; i < filesToTest.size(); i++) {
                String filename = filesToTest.get(i);

                if (i % 20 == 0 || i == filesToTest.size() - 1) {
                    double pct = ((double) (i + 1) / filesToTest.size() * 100);
                    System.out.print(String.format("\r    Progress: %d/%d (%.1f%%)", i + 1, filesToTest.size(), pct));
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

            System.out.println("\n");
            System.out.println("=".repeat(70));
            System.out.println("RESULTS");
            System.out.println("=".repeat(70));
            System.out.println("Passed: " + passed);
            System.out.println("Failed: " + failed);
            System.out.println(String.format("Success rate: %.2f%%", (double) passed / (passed + failed) * 100));

            if (!failures.isEmpty()) {
                System.out.println();
                System.out.println("Failures:");
                for (String[] f : failures) {
                    System.out.println("  - " + f[0]);
                    System.out.println("    Error: " + f[1]);
                }
            }

            System.out.println();

            // Test specific file lookup
            System.out.println("[3] Testing path resolution...");

            // Test case-insensitive lookup
            if (!allFiles.isEmpty()) {
                String testFile = allFiles.get(0);
                String upperCase = testFile.toUpperCase();
                String lowerCase = testFile.toLowerCase();

                ResolveResult resolved1 = grf.resolvePath(upperCase);
                ResolveResult resolved2 = grf.resolvePath(lowerCase);

                System.out.println("    Original: " + testFile);
                System.out.println("    Upper lookup: " + (resolved1 != null ? resolved1.status : "N/A"));
                System.out.println("    Lower lookup: " + (resolved2 != null ? resolved2.status : "N/A"));
            }

            System.out.println();

            // Test hasFile
            if (!allFiles.isEmpty()) {
                boolean exists1 = grf.hasFile(allFiles.get(0));
                boolean exists2 = grf.hasFile("nonexistent/file/path.txt");
                System.out.println("    hasFile (exists): " + exists1);
                System.out.println("    hasFile (not exists): " + exists2);
            }

            System.out.println();

            if (failed == 0) {
                System.out.println("✅ All read tests passed!");
                System.exit(0);
            } else {
                System.out.println("⚠️  " + failed + " read tests failed");
                System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
