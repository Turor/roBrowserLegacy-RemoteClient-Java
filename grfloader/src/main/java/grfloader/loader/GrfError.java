package turoran.robrowser.grfloader.loader;

import java.util.Map;

public class GrfError extends RuntimeException {
    public final GrfErrorCode code;
    public final Map<String, Object> context;

    public GrfError(GrfErrorCode code, String message) {
        this(code, message, null);
    }

    public GrfError(GrfErrorCode code, String message, Map<String, Object> context) {
        super(message);
        this.code = code;
        this.context = context;
    }
}
