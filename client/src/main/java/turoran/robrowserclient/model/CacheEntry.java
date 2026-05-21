package turoran.robrowserclient.model;


public record CacheEntry(byte[] data, String etag, int size) {
}
