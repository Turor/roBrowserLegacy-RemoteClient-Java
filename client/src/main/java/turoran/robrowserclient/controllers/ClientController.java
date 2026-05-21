package turoran.robrowserclient.controllers;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import turoran.robrowserclient.model.CacheEntry;
import turoran.robrowserclient.service.ClientService;
import turoran.robrowserclient.service.LRUCacheService;
import turoran.robrowserclient.util.StartupValidator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

@Controller("/")
public class ClientController {

    private static final Set<String> STATIC_EXTENSIONS = Set.of(
        ".grf", ".gat", ".rsw", ".gnd", ".rsm", ".str",
        ".spr", ".act", ".pal", ".bmp", ".tga", ".jpg", ".jpeg", ".png", ".gif",
        ".wav", ".mp3", ".ogg",
        ".txt", ".xml", ".lub", ".lua"
    );

    private final ClientService clientService;
    private final LRUCacheService lruCacheService;
    private final StartupValidator startupValidator;

    public ClientController(ClientService clientService, LRUCacheService lruCacheService, StartupValidator startupValidator) {
        this.clientService = clientService;
        this.lruCacheService = lruCacheService;
        this.startupValidator = startupValidator;
    }

    @Get("/{+path}")
    public HttpResponse<?> getFile(HttpRequest<?> request, @PathVariable(value = "path", defaultValue = "") String path) {
        // Serve index.html for root or empty path
        if (path == null || path.isEmpty() || path.equals("/")) {
            File indexFile = new File("index.html");
            if (!indexFile.exists()) {
                return HttpResponse.notFound("index.html not found. Please create an index.html file in the project root.");
            }
            try {
                byte[] content = Files.readAllBytes(indexFile.toPath());
                return HttpResponse.ok(content)
                    .contentType(MediaType.TEXT_HTML_TYPE)
                    .header("Cache-Control", "public, max-age=60");
            } catch (IOException e) {
                return HttpResponse.serverError("Error reading index.html");
            }
        }

        CacheEntry entry = clientService.getFileWithMetadata(path);
        if (entry == null) {
            return HttpResponse.notFound("File not found").header("Cache-Control", "no-store");
        }

        String etag = entry.etag();
        String ifNoneMatch = request.getHeaders().get("If-None-Match");
        if (ifNoneMatch != null && ifNoneMatch.equals("\"" + etag + "\"")) {
            return HttpResponse.notModified();
        }

        String lowerPath = path.toLowerCase();
        MediaType mediaType = getMediaType(lowerPath);

        var response = HttpResponse.ok(entry.data()).contentType(mediaType);

        String extension = "";
        int dotIndex = lowerPath.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = lowerPath.substring(dotIndex);
        }

        if (STATIC_EXTENSIONS.contains(extension)) {
            response.header("ETag", "\"" + etag + "\"")
                    .header("Cache-Control", "public, max-age=86400, immutable")
                    .header("Last-Modified", new Date().toString());
        } else {
            response.header("Cache-Control", "no-cache, no-store, must-revalidate");
        }

        return response;
    }

    private MediaType getMediaType(String path) {
        String lowerPath = path.toLowerCase();
        if (lowerPath.endsWith(".png")) return MediaType.IMAGE_PNG_TYPE;
        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) return MediaType.IMAGE_JPEG_TYPE;
        if (lowerPath.endsWith(".gif")) return MediaType.IMAGE_GIF_TYPE;
        if (lowerPath.endsWith(".html") || lowerPath.endsWith(".htm")) return MediaType.TEXT_HTML_TYPE;
        if (lowerPath.endsWith(".txt")) return MediaType.TEXT_PLAIN_TYPE;
        if (lowerPath.endsWith(".xml")) return MediaType.TEXT_XML_TYPE;
        if (lowerPath.endsWith(".json")) return MediaType.APPLICATION_JSON_TYPE;
        return MediaType.APPLICATION_OCTET_STREAM_TYPE;
    }

    @Post("/search")
    public HttpResponse<?> search(@Body Map<String, String> body) {
        String filter = body.get("filter");
        if (filter == null || filter.isEmpty()) {
            return HttpResponse.badRequest("Invalid filter");
        }
        if (!clientService.isSearchEnabled()) {
            return HttpResponse.badRequest("Search feature is disabled");
        }
        List<String> files = clientService.search(filter);
        return HttpResponse.ok(String.join("\n", files));
    }

    @Post("/batch")
    public HttpResponse<?> batch(@Body Map<String, List<String>> body) {
        List<String> files = body.get("files");
        if (files == null || files.isEmpty() || files.size() > 50) {
            return HttpResponse.badRequest(Map.of("error", "Invalid files array (1-50 files)"));
        }

        Map<String, String> results = new HashMap<>();
        for (String filePath : files) {
            byte[] data = clientService.getFile(filePath);
            if (data != null) {
                results.put(filePath, Base64.getEncoder().encodeToString(data));
            }
        }
        return HttpResponse.ok(results);
    }

    @Get("/stats")
    public Map<String, Object> getStats() {
        return Map.of(
            "cache", lruCacheService.getStats(),
            "index", clientService.getIndexStats()
        );
    }

    @Get("/files")
    public HttpResponse<List<String>> listFiles() {
        List<String> files = clientService.listFiles();
        return HttpResponse.ok(files).header("Cache-Control", "public, max-age=300");
    }

    @Get("/warm-cache")
    public Map<String, Object> warmCache(@QueryValue(defaultValue = "500") int limit) {
        int warmed = clientService.warmCache(null, limit);
        return Map.of("warmed", warmed);
    }

    @Get("/doctor")
    public String doctor(@QueryValue(defaultValue = "false") boolean deep) {
        Map<String, Object> results = startupValidator.validateAll(deep);
        // We can't easily capture System.out.println from StartupValidator here unless we change it to return a String
        // But let's just return the status for now or a formatted summary
        return "Check server console for detailed report. Success: " + results.get("success");
    }

    @Get("/status")
    public Map<String, Object> status() {
        return startupValidator.getStatusJSON();
    }
}
