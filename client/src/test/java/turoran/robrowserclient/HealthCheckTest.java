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
}
