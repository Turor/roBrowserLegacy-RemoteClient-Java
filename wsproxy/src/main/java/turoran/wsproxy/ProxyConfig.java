package turoran.wsproxy;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import lombok.Getter;

import java.util.List;

@Singleton
@Getter
public class ProxyConfig {

    @Value("${wsproxy.enabled:false}")
    private boolean enabled;

    @Value("${wsproxy.allowed-targets:127.0.0.1:6900,127.0.0.1:6121,127.0.0.1:5121}")
    private List<String> allowedTargets;

    public boolean isTargetAllowed(String target) {
        return allowedTargets != null && allowedTargets.contains(target);
    }
}
