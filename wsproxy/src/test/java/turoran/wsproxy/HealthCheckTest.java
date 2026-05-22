package turoran.wsproxy;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
public class HealthCheckTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void testHealthCheck() {
        String response = client.toBlocking().retrieve(HttpRequest.GET("/health"), String.class);
        assertTrue(response.contains("UP"));
    }

    @Test
    void testHealthCheckDetails() {
        String response = client.toBlocking().retrieve(HttpRequest.GET("/health"), String.class);
        // By default details might not be visible, but we added it to example.properties
        // In MicronautTest, it might not pick up example.properties unless specified.
        // But the basic check passed.
        assertTrue(response.contains("status"));
        assertTrue(response.contains("origins"));
        assertTrue(response.contains("accepted"));
        assertTrue(response.contains("rodata.turoran.com"));
    }
}
