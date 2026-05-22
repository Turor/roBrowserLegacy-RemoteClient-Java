package turoran.robrowserclient;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(environments = "test")
@Property(name = "micronaut.server.port", value = "-1")
@Property(name = "client.rootpath", value = ".")
public class HealthCheckTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void testHealthEndpoint() {
        Map<String, Object> response = client.toBlocking().retrieve(HttpRequest.GET("/health-check"), Map.class);
        assertNotNull(response);
        assertTrue(response.containsKey("status"));
        assertTrue(response.containsKey("timestamp"));
        assertTrue(response.containsKey("jvm"));
    }

    @Test
    void testHealthSimpleEndpoint() {
        Map<String, Object> response = client.toBlocking().retrieve(HttpRequest.GET("/health/simple"), Map.class);
        assertNotNull(response);
        assertTrue(response.containsKey("status"));
        assertTrue(response.containsKey("grfsLoaded"));
    }

    @Test
    void testDoctorEndpoint() {
        String response = client.toBlocking().retrieve(HttpRequest.GET("/doctor"), String.class);
        assertNotNull(response);
        assertTrue(response.contains("roBrowser Remote Client - Doctor"));
        assertTrue(response.contains("SYSTEM STATS"));
    }
    @Test
    void testApiHealth() {
        Map<String, Object> response = client.toBlocking().retrieve(HttpRequest.GET("/api/health"), Map.class);
        assertNotNull(response);
        assertEquals("error", response.get("status")); // error because no GRFs in test env likely
    }

    @Test
    void testApiHealthSimple() {
        Map<String, Object> response = client.toBlocking().retrieve(HttpRequest.GET("/api/health/simple"), Map.class);
        assertNotNull(response);
        assertTrue(response.containsKey("status"));
    }

    @Test
    void testApiValidate() {
        String response = client.toBlocking().retrieve(HttpRequest.GET("/api/validate"), String.class);
        assertNotNull(response);
        assertTrue(response.contains("Doctor"));
    }

    @Test
    void testApiCacheStats() {
        Map<String, Object> response = client.toBlocking().retrieve(HttpRequest.GET("/api/cache-stats"), Map.class);
        assertNotNull(response);
        assertTrue(response.containsKey("cache"));
    }

    @Test
    void testApiMissingFiles() {
        String response = client.toBlocking().retrieve(HttpRequest.GET("/api/missing-files"), String.class);
        assertNotNull(response);
        assertTrue(response.startsWith("[") && response.endsWith("]"));
    }

    @Test
    void testApiMissingFilesClear() {
        Map<String, Object> response = client.toBlocking().retrieve(HttpRequest.POST("/api/missing-files/clear", Map.of()), Map.class);
        assertNotNull(response);
        assertEquals("Missing files log cleared", response.get("message"));
    }

    @Test
    void testApiWarmCache() {
        Map<String, Object> response = client.toBlocking().retrieve(HttpRequest.GET("/api/warm-cache"), Map.class);
        assertNotNull(response);
        assertTrue(response.containsKey("enabled"));
    }

    @Test
    void testApiWarmCacheRun() {
        Map<String, Object> response = client.toBlocking().retrieve(HttpRequest.POST("/api/warm-cache/run", Map.of()), Map.class);
        assertNotNull(response);
        assertTrue(response.containsKey("warmed"));
    }

    @Test
    void testApiPathMapping() {
        Map<String, Object> response = client.toBlocking().retrieve(HttpRequest.GET("/api/path-mapping"), Map.class);
        assertNotNull(response);
        assertTrue(response.containsKey("usePathMappings"));
    }
}
