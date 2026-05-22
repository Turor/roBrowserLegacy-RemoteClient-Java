package turoran.wsproxy;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(environments = "test")
@Property(name = "micronaut.server.port", value = "-1")
@Property(name = "wsproxy.enabled", value = "true")
@Property(name = "wsproxy.allowed-targets", value = "127.0.0.1:6900,127.0.0.1:6121,*.example.com:80,localhost:8080")
public class ProxyConfigTest {

    @Inject
    ProxyConfig proxyConfig;

    @Test
    void testConfig() {
        assertTrue(proxyConfig.isEnabled());
        assertEquals(4, proxyConfig.getAllowedTargets().size());
        assertTrue(proxyConfig.getAllowedTargets().contains("127.0.0.1:6900"));
        assertTrue(proxyConfig.getAllowedTargets().contains("127.0.0.1:6121"));
        assertTrue(proxyConfig.isTargetAllowed("127.0.0.1:6900"));
        assertTrue(proxyConfig.isTargetAllowed("127.0.0.1:6121"));
        assertFalse(proxyConfig.isTargetAllowed("127.0.0.1:5121"));
        assertFalse(proxyConfig.isTargetAllowed("google.com:80"));
    }

    @Test
    void testWildcardSubmasking() {
        assertTrue(proxyConfig.isTargetAllowed("sub.example.com:80"));
        assertTrue(proxyConfig.isTargetAllowed("example.com:80"));
        assertFalse(proxyConfig.isTargetAllowed("badexample.com:80"));
        assertFalse(proxyConfig.isTargetAllowed("sub.example.com:81"));
        assertFalse(proxyConfig.isTargetAllowed("other.com:80"));
    }

    @Test
    void testDnsLookup() {
        // localhost should resolve to 127.0.0.1
        assertTrue(proxyConfig.isTargetAllowed("127.0.0.1:8080"));
        // 127.0.0.1:6900 is in allowed targets, so localhost:6900 should also work
        assertTrue(proxyConfig.isTargetAllowed("localhost:6900"));
    }
}
