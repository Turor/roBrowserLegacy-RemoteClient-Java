package turoran.robrowser.client.model;


public record CacheEntry(byte[] data, String etag, int size) {
}
