package turoran.robrowserclient.errors;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Singleton
public class IOExceptionHandler implements ExceptionHandler<IOException, HttpResponse<?>> {
    private static final Logger LOG = LoggerFactory.getLogger(IOExceptionHandler.class);

    @Override
    @Error(global = true)
    public HttpResponse<?> handle(HttpRequest request, IOException exception) {
        String message = exception.getMessage();
        if (message != null && (
                message.contains("An established connection was aborted") ||
                message.contains("Connection reset by peer") ||
                message.contains("Broken pipe") ||
                message.contains("An existing connection was forcibly closed")
        )) {
            // Log as debug because this is usually just the client closing the connection early
            LOG.debug("Client closed connection: {} - Path: {}", message, request.getPath());
            return null; // Let Micronaut handle it or return nothing as connection is closed anyway
        }

        LOG.error("IOException occurred: {} - Path: {}", message, request.getPath(), exception);
        return HttpResponse.serverError(new JsonError("Internal Server Error: " + message));
    }
}
