package turoran.grfloader.benchmark;

import org.junit.jupiter.api.Test;
import turoran.grfloader.loader.GRFNode;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;

public class GRFBenchmarkTest {

    private static File getResourceFile() throws Exception {
        URL url = GRFBenchmarkTest.class.getClassLoader().getResource("with-files.grf");
        if (url == null) {
            throw new RuntimeException("Resource not found: " + "with-files.grf");
        }
        return new File(url.toURI());
    }

    @Test
    public void runBenchmarks() throws Exception {
        PerformanceBenchmark bench = new PerformanceBenchmark();
        File grfFile = getResourceFile();
        String grfPath = grfFile.getAbsolutePath();

        System.out.println("🚀 Starting GRF Loader benchmarks...\n");
        System.out.println("📁 Test file: " + grfPath + "\n");

        // Benchmark 1: Load GRF file
        bench.benchmark(
            "Load GRF file",
            () -> {
                try (RandomAccessFile fd = new RandomAccessFile(grfFile, "r")) {
                    GRFNode grf = new GRFNode(fd);
                    grf.load();
                }
            },
            50
        );

        // Benchmark 2: Extract single uncompressed file
        bench.benchmark(
            "Extract uncompressed file (raw)",
            () -> {
                try (RandomAccessFile fd = new RandomAccessFile(grfFile, "r")) {
                    GRFNode grf = new GRFNode(fd);
                    grf.load();
                    grf.getFile("raw");
                }
            },
            50
        );

        // Benchmark 3: Extract compressed file
        bench.benchmark(
            "Extract compressed file (compressed)",
            () -> {
                try (RandomAccessFile fd = new RandomAccessFile(grfFile, "r")) {
                    GRFNode grf = new GRFNode(fd);
                    grf.load();
                    grf.getFile("compressed");
                }
            },
            50
        );

        // Benchmark 6: Extract all files
        bench.benchmark(
            "Extract ALL files",
            () -> {
                try (RandomAccessFile fd = new RandomAccessFile(grfFile, "r")) {
                    GRFNode grf = new GRFNode(fd);
                    grf.load();

                    for (String filename : grf.files.keySet()) {
                        grf.getFile(filename);
                    }
                }
            },
            20
        );

        // Benchmark 7: Repeated extraction (cache test)
        bench.benchmark(
            "Repeated extraction (10x same file)",
            () -> {
                try (RandomAccessFile fd = new RandomAccessFile(grfFile, "r")) {
                    GRFNode grf = new GRFNode(fd);
                    grf.load();

                    for (int i = 0; i < 10; i++) {
                        grf.getFile("compressed");
                    }
                }
            },
            20
        );

        bench.printResults();

        // Save results to JSON
        String outputDir = System.getProperty("test.output.dir", "build/test-results/benchmark");
        File resultsFile = writeFile(outputDir, bench);
        System.out.println("💾 Results saved to " + resultsFile.getPath() + "\n");
    }

    private static File writeFile(String outputDir, PerformanceBenchmark bench) throws IOException {
        File dir = new File(outputDir);
        if (!dir.exists()) {
            if(!dir.mkdirs()){
                throw new RuntimeException("Failed to create output directory: " + outputDir);
            }
        }
        File resultsFile = new File(dir, "benchmark-results.json");
        try (java.io.FileWriter writer = new java.io.FileWriter(resultsFile)) {
            writer.write("[\n");
            java.util.List<BenchmarkResult> results = bench.getResults();
            for (int i = 0; i < results.size(); i++) {
                BenchmarkResult res = results.get(i);
                writer.write(String.format(java.util.Locale.US,
                    "  {\n    \"name\": \"%s\",\n    \"duration\": %.2f,\n    \"iterations\": %d,\n    \"avgTime\": %.2f,\n    \"opsPerSec\": %.2f,\n    \"memoryUsed\": %d\n  }%s\n",
                    res.name, res.durationMs, res.iterations, res.avgTimeMs, res.opsPerSec, res.memoryUsedBytes,
                    (i < results.size() - 1 ? "," : "")
                ));
            }
            writer.write("]\n");
        }
        return resultsFile;
    }
}
