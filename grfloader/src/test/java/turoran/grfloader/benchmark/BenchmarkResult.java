package turoran.grfloader.benchmark;

public class BenchmarkResult {
    public final String name;
    public final double durationMs;
    public final int iterations;
    public final double avgTimeMs;
    public final double opsPerSec;
    public final Long memoryUsedBytes;

    public BenchmarkResult(String name, double durationMs, int iterations, double avgTimeMs, double opsPerSec, Long memoryUsedBytes) {
        this.name = name;
        this.durationMs = durationMs;
        this.iterations = iterations;
        this.avgTimeMs = avgTimeMs;
        this.opsPerSec = opsPerSec;
        this.memoryUsedBytes = memoryUsedBytes;
    }
}
