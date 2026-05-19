package turoran.robrowserlegacy.model;


public record CacheEntry(byte[] data, String etag, int size) {
}
