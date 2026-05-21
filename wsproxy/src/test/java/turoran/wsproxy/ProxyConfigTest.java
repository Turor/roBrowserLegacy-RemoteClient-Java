package turoran.wsproxy;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@Property(name = "wsproxy.enabled", value = "true")
@Property(name = "wsproxy.allowed-targets", value = "127.0.0.1:6900,127.0.0.1:6121")
public class ProxyConfigTest {

    @Inject
    ProxyConfig proxyConfig;

    @Test
    void testConfig() {
        assertTrue(proxyConfig.isEnabled());
        assertEquals(List.of("127.0.0.1:6900", "127.0.0.1:6121"), proxyConfig.getAllowedTargets());
        assertTrue(proxyConfig.isTargetAllowed("127.0.0.1:6900"));
        assertTrue(proxyConfig.isTargetAllowed("127.0.0.1:6121"));
        assertFalse(proxyConfig.isTargetAllowed("127.0.0.1:5121"));
        assertFalse(proxyConfig.isTargetAllowed("google.com:80"));
    }
}
