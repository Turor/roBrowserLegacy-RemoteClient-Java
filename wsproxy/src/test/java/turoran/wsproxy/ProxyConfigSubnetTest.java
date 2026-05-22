package turoran.wsproxy;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(environments = "test")
@Property(name = "micronaut.server.port", value = "-1")
@Property(name = "wsproxy.enabled", value = "true")
@Property(name = "wsproxy.allowed-targets", value = "192.168.*.*:6900,10.0.0.0/8:8080")
public class ProxyConfigSubnetTest {

    @Inject
    ProxyConfig proxyConfig;

    @Test
    void testWildcardIp() {
        // 192.168.*.*:6900
        assertTrue(proxyConfig.isTargetAllowed("192.168.11.110:6900"), "Should allow 192.168.11.110:6900 based on 192.168.*.*:6900");
        assertTrue(proxyConfig.isTargetAllowed("192.168.1.1:6900"), "Should allow 192.168.1.1:6900 based on 192.168.*.*:6900");
        assertFalse(proxyConfig.isTargetAllowed("192.167.1.1:6900"), "Should NOT allow 192.167.1.1:6900 based on 192.168.*.*:6900");
    }

    @Test
    void testCidrIp() {
        // This is what we want to support
        assertTrue(proxyConfig.isTargetAllowed("10.0.0.1:8080"), "Should allow 10.0.0.1:8080 based on 10.0.0.0/8:8080");
        assertTrue(proxyConfig.isTargetAllowed("10.255.255.255:8080"), "Should allow 10.255.255.255:8080 based on 10.0.0.0/8:8080");
        assertFalse(proxyConfig.isTargetAllowed("11.0.0.1:8080"), "Should NOT allow 11.0.0.1:8080 based on 10.0.0.0/8:8080");
    }
}
