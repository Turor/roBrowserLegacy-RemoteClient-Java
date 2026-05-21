package turoran.grfloader.loader;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Using this Browser, we work from a File object.
 * We use RandomAccessFile to read only some part of the file to avoid
 * loading large files into memory.
 * <a href="https://github.com/FranciscoWallison/grf-loader/blob/main/src/grf-browser.ts">GRFBrowser JS source</a>
 */
public class GRFBrowser extends GRFBase<File> {

    public GRFBrowser(File file) {
        super(file);
    }

    public GRFBrowser(File file, GrfOptions options) {
        super(file, options);
    }

    @Override
    public byte[] getStreamBuffer(File file, long offset, int length) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(offset);
            byte[] buffer = new byte[length];
            raf.readFully(buffer);
            return buffer;
        }
    }
}
