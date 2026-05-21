package turoran.robrowserclient.service;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import turoran.robrowserclient.model.CacheEntry;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(environments = "test")
@Property(name = "micronaut.server.port", value = "-1")
@Property(name = "client.rootpath", value = "build/resources/test")
@Property(name = "client.datapath", value = "Data")
@Property(name = "client.dataini", value = "DATA.INI")
@Property(name = "client.public-url", value = "http://localhost:0")
@Property(name = "client.usepathmappings", value = "true")
public class ClientServiceTest {

    @Inject
    LRUCacheService fileCache;

    @Inject
    ClientService clientService;

    @Value("${client.datapath:Data}")
    private String dataPathName;

    @BeforeEach
    void setup() throws IOException {
        Path rootPath = Path.of("build/resources/test");
        Path resourcesPath = rootPath.resolve("resources");
        Files.createDirectories(resourcesPath);
        
        Path testGrf = resourcesPath.resolve("test.grf");
        if (!Files.exists(testGrf)) {
            Files.copy(Path.of("../grfloader/src/test/resources/with-files.grf"), testGrf);
        }

        Path dataIni = resourcesPath.resolve("DATA.INI");
        String iniContent = "[Data]\n0=test.grf\n";
        Files.writeString(dataIni, iniContent, Charset.forName("CP949"));

        // Force re-init if possible or just use it if it's already initialized correctly
        clientService.init();
    }

    @AfterEach
    void cleanup() {
        clientService.cleanup();
        fileCache.clear();
    }

    @Test
    void testFileRetrievalFromGRF() {
        byte[] data = clientService.getFile("raw");
        assertNotNull(data, "File 'raw' should be found in test.grf");
        String content = new String(data).trim();
        assertTrue(content.contains("test"), "Content should contain 'test'");
    }

    @Test
    void testFileRetrievalWithMetadata() {
        CacheEntry entry = clientService.getFileWithMetadata("raw");
        assertNotNull(entry);
        assertTrue(new String(entry.data()).trim().contains("test"), "Content should contain 'test'");
        assertNotNull(entry.etag());
    }

    @Test
    void testFileNotFound() {
        byte[] data = clientService.getFile("non-existent");
        assertNull(data);
    }

    @Test
    void testListFiles() {
        List<String> files = clientService.listFiles();
        assertNotNull(files);
        assertTrue(files.contains("raw"));
        assertTrue(files.contains("compressed"));
    }

    @Test
    void testSearch() {
        List<String> results = clientService.search("ra.");
        assertTrue(results.contains("raw"));
        
        results = clientService.search("comp");
        assertTrue(results.contains("compressed"));
    }

    @Test
    void testFileRetrievalFromLocal() throws IOException {
        Path rootPath = Path.of("build/resources/test");
        Path localFile = rootPath.resolve("resources").resolve("local.txt");
        Files.createDirectories(localFile.getParent());
        Files.writeString(localFile, "local content");

        byte[] data = clientService.getFile("local.txt");
        assertNotNull(data);
        assertEquals("local content", new String(data));
        
        // Test that it's cached
        assertTrue(fileCache.has("local.txt"));
    }

    @Test
    void testPathMapping() throws IOException {
        Path rootPath = Path.of("build/resources/test");
        Path mappingFile = rootPath.resolve("resources").resolve("path-mapping.json");
        Files.createDirectories(mappingFile.getParent());
        String mappingJson = "{\"paths\": {\"mapped/path.txt\": \"raw\"}}";
        Files.writeString(mappingFile, mappingJson);
        
        // Re-init to load mapping
        clientService.init();
        
        byte[] data = clientService.getFile("mapped/path.txt");
        assertNotNull(data, "Mapped path should return content from 'raw'");
        assertTrue(new String(data).trim().contains("test"));
    }

    @Test
    void testWarmCache() {
        // Use a pattern that matches files in with-files.grf (e.g., "raw" or "compressed")
        int warmed = clientService.warmCache(List.of(Pattern.compile("raw")), 10);
        assertTrue(warmed >= 1, "Should have warmed at least one file");
        assertTrue(fileCache.has("raw"), "Cache should contain 'raw'");
    }

    @Test
    void testAutoGeneratePathMapping() throws IOException {
        Path rootPath = Path.of("build/resources/test");
        Path mappingFile = rootPath.resolve("resources").resolve("path-mapping.json");
        
        // Ensure it doesn't exist
        Files.deleteIfExists(mappingFile);
        
        clientService.init();
        
        assertTrue(Files.exists(mappingFile), "path-mapping.json should have been auto-generated");
        String content = Files.readString(mappingFile);
        assertTrue(content.contains("\"paths\""), "Generated file should contain 'paths'");
    }

    @Test
    void testBgmRetrieval() throws IOException {
        Path rootPath = Path.of("build/resources/test");
        Path bgmPath = rootPath.resolve("BGM");
        Files.createDirectories(bgmPath);
        Files.writeString(bgmPath.resolve("test.mp3"), "fake mp3 content");
        
        byte[] data = clientService.getFile("BGM/test.mp3");
        assertNotNull(data);
        assertEquals("fake mp3 content", new String(data));

        data = clientService.getFile("BGM\\test.mp3");
        assertNotNull(data);
        assertEquals("fake mp3 content", new String(data));
    }

    @Test
    void testRequiredDirectoriesCreated() throws IOException {
        Path rootPath = Path.of("build/resources/test/required_dirs_test");
        if (Files.exists(rootPath)) {
            // Clean up if it exists
            try (var stream = Files.walk(rootPath)) {
                stream.sorted(Comparator.reverseOrder())
                      .forEach(p -> {
                          try {
                              Files.delete(p);
                          } catch (IOException e) {
                              // ignore
                          }
                      });
            }
        }
        Files.createDirectories(rootPath);
        
        // Use a reflection to set rootPath or just use property injection if we can trigger another bean creation
        // But for unit test, we can just call init if we make rootPath accessible or just trust it.
        // Actually, let's just check if it's created in the default rootPath of the test
        Path baseRoot = Path.of("build/resources/test");
        String[] dirs = {"BGM", "Data", "AI", "System"};
        
        // Since setup already calls init(), they should already be there.
        // Let's delete them and call init() again.
        for (String dir : dirs) {
            Path p = baseRoot.resolve(dir);
            if (Files.exists(p)) {
                try (var stream = Files.walk(p)) {
                    stream.sorted(Comparator.reverseOrder())
                          .forEach(path -> {
                              try {
                                  Files.delete(path);
                              } catch (IOException e) {
                                  // ignore
                              }
                          });
                }
            }
        }
        
        clientService.init();
        
        for (String dir : dirs) {
            assertTrue(Files.exists(baseRoot.resolve(dir)), "Directory " + dir + " should be created");
            assertTrue(Files.isDirectory(baseRoot.resolve(dir)), dir + " should be a directory");
        }
    }
}
