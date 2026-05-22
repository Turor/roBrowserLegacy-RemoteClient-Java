package turoran.wsproxy;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.regex.Pattern;

@Singleton
@Getter
@Slf4j
public class ProxyConfig {

    @Value("${wsproxy.enabled:false}")
    private boolean enabled;

    @Value("${wsproxy.allowed-targets:127.0.0.1:6900,127.0.0.1:6121,127.0.0.1:5121}")
    private Set<String> allowedTargets;

    public boolean isTargetAllowed(String target) {
        if (allowedTargets == null || target == null) {
            return false;
        }

        if (allowedTargets.contains(target)) {
            return true;
        }

        int colonIdx = target.lastIndexOf(':');
        if (colonIdx == -1) {
            return false;
        }

        String host = target.substring(0, colonIdx);
        String port = target.substring(colonIdx + 1);

        for (String allowed : allowedTargets) {
            int allowedColonIdx = allowed.lastIndexOf(':');
            if (allowedColonIdx == -1) continue;

            String allowedHost = allowed.substring(0, allowedColonIdx);
            String allowedPort = allowed.substring(allowedColonIdx + 1);

            if (!port.equals(allowedPort)) {
                continue;
            }

            // Check wildcard submasking (e.g., *.example.com)
            if (allowedHost.startsWith("*.")) {
                String suffix = allowedHost.substring(2); // example.com
                if (host.equals(suffix) || host.endsWith("." + suffix)) {
                    return true;
                }
            }

            // Check wildcard IP (e.g., 192.168.*.*)
            if (allowedHost.contains("*")) {
                String regex = allowedHost.replace(".", "\\.").replace("*", ".*");
                if (Pattern.matches(regex, host)) {
                    return true;
                }
            }

            // Check CIDR notation (e.g., 192.168.0.0/16)
            if (allowedHost.contains("/")) {
                try {
                    if (isIpInSubnet(host, allowedHost)) {
                        return true;
                    }
                } catch (Exception e) {
                    log.debug("Failed to check CIDR subnet: {} for host: {}", allowedHost, host, e);
                }
            }

            // DNS lookup comparison
            try {
                InetAddress targetAddr = InetAddress.getByName(host);
                InetAddress allowedAddr = InetAddress.getByName(allowedHost);
                if (targetAddr.equals(allowedAddr)) {
                    return true;
                }
            } catch (UnknownHostException ignored) {
                // Ignore resolution failures and move to next allowed target
            }
        }

        return false;
    }

    private boolean isIpInSubnet(String ipAddress, String cidr) throws UnknownHostException {
        String[] parts = cidr.split("/");
        String subnetAddress = parts[0];
        int prefixLength = Integer.parseInt(parts[1]);

        InetAddress addr = InetAddress.getByName(ipAddress);
        InetAddress subnetAddr = InetAddress.getByName(subnetAddress);

        byte[] addrBytes = addr.getAddress();
        byte[] subnetBytes = subnetAddr.getAddress();

        if (addrBytes.length != subnetBytes.length) {
            return false;
        }

        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;

        for (int i = 0; i < fullBytes; i++) {
            if (addrBytes[i] != subnetBytes[i]) {
                return false;
            }
        }

        if (remainingBits > 0) {
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            return (addrBytes[fullBytes] & mask) == (subnetBytes[fullBytes] & mask);
        }

        return true;
    }
}
