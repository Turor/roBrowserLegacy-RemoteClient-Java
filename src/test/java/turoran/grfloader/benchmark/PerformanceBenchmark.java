package turoran.grfloader.benchmark;

import java.util.ArrayList;
import java.util.List;

public class PerformanceBenchmark {
    private final List<BenchmarkResult> results = new ArrayList<>();

    public interface BenchmarkTask {
        void run() throws Exception;
    }

    public BenchmarkResult benchmark(String name, BenchmarkTask task, int iterations) throws Exception {
        // Warmup
        task.run();

        // Suggest GC
        System.gc();
        Thread.sleep(100);

        long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long startTime = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            task.run();
        }

        long endTime = System.nanoTime();
        long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        double durationMs = (endTime - startTime) / 1_000_000.0;
        double avgTimeMs = durationMs / iterations;
        double opsPerSec = 1000.0 / avgTimeMs;
        long memoryUsed = memAfter - memBefore;

        BenchmarkResult result = new BenchmarkResult(
                name,
                durationMs,
                iterations,
                avgTimeMs,
                opsPerSec,
                memoryUsed
        );

        this.results.add(result);
        return result;
    }

    public void printResults() {
        System.out.println("GRF LOADER - PERFORMANCE BENCHMARK");

        for (BenchmarkResult result : results) {
            System.out.printf("- %s\n", result.name);
            System.out.printf("  Total time: %.2fms\n", result.durationMs);
            System.out.printf("  Iterations: %d\n", result.iterations);
            System.out.printf("  Average: %.2fms/op\n", result.avgTimeMs);
            System.out.printf("  Throughput: %.2f ops/sec\n", result.opsPerSec);
            if (result.memoryUsedBytes != null) {
                System.out.printf("  Memory: %.2f MB\n", result.memoryUsedBytes / 1024.0 / 1024.0);
            }
        }
    }

    public List<BenchmarkResult> getResults() {
        return results;
    }
}
