package turoran.robrowserclient;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Singleton
public class OriginHealthIndicator implements HealthIndicator {

    @Value("${micronaut.server.cors.configurations.web.allowedOrigins:}")
    @Nullable
    protected List<String> allowedOrigins;

    @Override
    public Publisher<HealthResult> getResult() {
        HealthResult.Builder builder = HealthResult.builder("origins");
        builder.status(HealthStatus.UP);
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            builder.details(Collections.singletonMap("accepted", allowedOrigins));
        } else {
            builder.details(Collections.singletonMap("accepted", "none"));
        }
        return Mono.just(builder.build());
    }
}
