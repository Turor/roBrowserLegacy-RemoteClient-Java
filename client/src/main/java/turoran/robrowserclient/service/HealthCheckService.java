package turoran.robrowserclient.service;

import jakarta.inject.Singleton;
import turoran.robrowserclient.model.CacheStats;
import turoran.robrowserclient.util.StartupValidator;

import java.time.Instant;
import java.util.*;

@Singleton
public class HealthCheckService {

    private final StartupValidator startupValidator;
    private final ClientService clientService;
    private final LRUCacheService lruCacheService;

    public HealthCheckService(StartupValidator startupValidator, ClientService clientService, LRUCacheService lruCacheService) {
        this.startupValidator = startupValidator;
        this.clientService = clientService;
        this.lruCacheService = lruCacheService;
    }

    public Map<String, Object> getFullStatus() {
        Map<String, Object> startupResults = startupValidator.getResults();
        Map<String, Object> status = new LinkedHashMap<>();
        
        status.put("status", (boolean) startupResults.get("success") ? "ok" : "error");
        status.put("timestamp", Instant.now().toString());
        status.put("version", getVersionInfo());
        status.put("jvm", getJvmInfo());
        status.put("grfs", startupResults.get("details"));
        status.put("cache", lruCacheService.getStats());
        status.put("index", clientService.getIndexStats());
        
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) startupResults.get("warnings");
        status.put("warnings", warnings);
        status.put("hasWarnings", !warnings.isEmpty());

        return status;
    }

    public Map<String, Object> getSimpleStatus() {
        Map<String, Object> indexStats = clientService.getIndexStats();
        int grfCount = (int) indexStats.getOrDefault("grfCount", 0);
        boolean ok = grfCount > 0;

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", ok ? "ok" : "error");
        status.put("timestamp", Instant.now().toString());
        status.put("grfsLoaded", grfCount);
        status.put("message", ok 
            ? "System operational with " + grfCount + " GRF(s)" 
            : "No valid GRF files loaded");
        
        return status;
    }

    private Map<String, Object> getVersionInfo() {
        return Map.of(
            "remoteclient", "2.1.0",
            "features", Map.of(
                "lruCache", true,
                "fileIndex", true,
                "grf0x300", true,
                "healthCheck", true
            )
        );
    }

    private Map<String, Object> getJvmInfo() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("version", System.getProperty("java.version"));
        info.put("vendor", System.getProperty("java.vendor"));
        info.put("memoryLimit", formatBytes(runtime.maxMemory()));
        info.put("memoryUsage", formatBytes(runtime.totalMemory() - runtime.freeMemory()));
        info.put("memoryTotal", formatBytes(runtime.totalMemory()));
        return info;
    }

    private String formatBytes(long bytes) {
        String[] units = {"B", "KB", "MB", "GB"};
        double val = bytes;
        int i = 0;
        while (val >= 1024 && i < units.length - 1) {
            val /= 1024;
            i++;
        }
        return String.format("%.2f %s", val, units[i]);
    }

    public String getDoctorReport(boolean deep) {
        Map<String, Object> results = startupValidator.validateAll(deep);
        StringBuilder sb = new StringBuilder();

        sb.append("\n");
        sb.append("╔════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║            🏥 roBrowser Remote Client - Doctor (Java)                      ║\n");
        sb.append("║                        System Diagnosis                                    ║\n");
        sb.append("╚════════════════════════════════════════════════════════════════════════════╝\n");
        sb.append("\n");

        if (deep) {
            sb.append("🔬 Deep encoding validation enabled (this may take a while...)\n\n");
        }

        // Print Info
        @SuppressWarnings("unchecked")
        List<String> info = (List<String>) results.get("info");
        for (String msg : info) {
            sb.append("✓ ").append(msg).append("\n");
        }

        // Print Warnings
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) results.get("warnings");
        if (!warnings.isEmpty()) {
            sb.append("\n⚠️  WARNINGS:\n");
            for (String msg : warnings) {
                sb.append("   - ").append(msg).append("\n");
            }
        }

        // Print Errors
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) results.get("errors");
        if (!errors.isEmpty()) {
            sb.append("\n❌ ERRORS:\n");
            for (String msg : errors) {
                sb.append("   - ").append(msg).append("\n");
            }
        }

        // Stats from ClientService and LRUCache
        sb.append("\n📊 SYSTEM STATS:\n");
        Map<String, Object> indexStats = clientService.getIndexStats();
        sb.append("   - GRFs Loaded:       ").append(indexStats.get("grfCount")).append("\n");
        sb.append("   - Files Indexed:     ").append(String.format("%,d", indexStats.get("totalFiles"))).append("\n");
        
        CacheStats cacheStats = lruCacheService.getStats();
        sb.append("   - Cache Items:       ").append(String.format("%,d", cacheStats.size())).append("\n");
        sb.append("   - Cache Hit Rate:    ").append(cacheStats.hitRate()).append("\n");
        sb.append("   - Memory Used:       ").append(cacheStats.memoryUsedMB()).append(" MB / ").append(cacheStats.maxMemoryMB()).append(" MB\n");

        sb.append("\n");
        if (!(boolean) results.get("success")) {
            sb.append("💡 Fix the errors above and run this command again.\n");
        } else {
            if (!warnings.isEmpty()) {
                sb.append("💡 Consider addressing the warnings for optimal performance.\n");
            }
            sb.append("🎉 System is configured correctly! You can start the server.\n");
            if (!deep) {
                sb.append("\n💡 Tip: Use ?deep=true for detailed encoding analysis\n");
            }
        }
        sb.append("\n");

        return sb.toString();
    }
}
