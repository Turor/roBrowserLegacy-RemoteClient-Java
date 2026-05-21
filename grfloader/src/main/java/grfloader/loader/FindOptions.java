package turoran.robrowser.grfloader.loader;

import java.util.regex.Pattern;

public class FindOptions {
    /** Filter by file extension (without dot, e.g., 'spr', 'act') */
    public String ext;
    /** Filter by substring in path */
    public String contains;
    /** Filter by path ending */
    public String endsWith;
    /** Filter by regex pattern */
    public Pattern regex;
    /** Maximum results to return (default: unlimited) */
    public Integer limit;
}
