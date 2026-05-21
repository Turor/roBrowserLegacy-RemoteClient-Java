package turoran.grfloader.loader;

import java.util.List;

/** Result of path resolution */
public class ResolveResult {
    public enum Status { FOUND, NOT_FOUND, AMBIGUOUS }
    
    public Status status;
    /** The exact matched path (if found) */
    public String matchedPath;
    /** All candidate paths (if ambiguous) */
    public List<String> candidates;

    public ResolveResult(Status status) {
        this.status = status;
    }

    public ResolveResult(Status status, String matchedPath) {
        this.status = status;
        this.matchedPath = matchedPath;
    }

    public ResolveResult(Status status, List<String> candidates) {
        this.status = status;
        this.candidates = candidates;
    }
}
