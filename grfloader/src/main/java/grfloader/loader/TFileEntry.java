package turoran.robrowser.grfloader.loader;

public class TFileEntry {
    public int type;
    public long offset;
    public int realSize;
    public int compressedSize;
    public int lengthAligned;
    /** Raw filename bytes for re-decoding if needed */
    public byte[] rawNameBytes;

    @Override
    public String toString() {
        return "TFileEntry{" +
                "type=" + type +
                ", offset=" + offset +
                ", realSize=" + realSize +
                ", compressedSize=" + compressedSize +
                ", lengthAligned=" + lengthAligned +
                '}';
    }
}
