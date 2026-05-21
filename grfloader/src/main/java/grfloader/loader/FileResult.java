package turoran.robrowser.grfloader.loader;

public class FileResult {
    public byte[] data;
    public String error;

    public FileResult(byte[] data, String error) {
        this.data = data;
        this.error = error;
    }
}
