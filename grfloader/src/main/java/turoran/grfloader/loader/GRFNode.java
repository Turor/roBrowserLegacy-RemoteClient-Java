package turoran.grfloader.loader;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/**
 * Options for GRFNode
 * <a href="https://github.com/FranciscoWallison/grf-loader/blob/main/src/grf-node.ts">GRFNode</a>
 */

public class GRFNode extends GRFBase<RandomAccessFile> {
    private final boolean useBufferPool;

    public GRFNode(RandomAccessFile fd) {
        this(fd, new GRFNodeOptions());
    }

    public GRFNode(RandomAccessFile fd, GRFNodeOptions options) {
        super(fd, options);
        this.useBufferPool = options != null ? options.useBufferPool : true;

        try {
            // In Java, if we have a RandomAccessFile, we check if it's valid by getting its length
            // or checking if the FD is valid.
            if (fd == null || fd.getFD() == null || !fd.getFD().valid()) {
                throw new Error("GRFNode: invalid file descriptor");
            }
        } catch (IOException e) {
            throw new Error("GRFNode: invalid file descriptor");
        }
    }

    @Override
    public byte[] getStreamBuffer(RandomAccessFile fd, long offset, int length) throws IOException {
        ByteBuffer buffer = this.useBufferPool
                ? BufferPool.INSTANCE.acquire(length)
                : ByteBuffer.allocate(length);

        try {
            fd.seek(offset);
            byte[] array = buffer.array();
            int bytesRead = fd.read(array, 0, length);

            if (bytesRead != length) {
                // Release buffer back to pool if read failed
                if (this.useBufferPool) {
                    BufferPool.INSTANCE.release(buffer);
                }
                throw new IOException("Not a GRF file (invalid signature)");
            }

            // Return exactly the requested length
            if (buffer.array().length == length) {
                return buffer.array();
            } else {
                byte[] result = new byte[length];
                System.arraycopy(buffer.array(), 0, result, 0, length);
                if (this.useBufferPool) {
                    BufferPool.INSTANCE.release(buffer);
                }
                return result;
            }
        } catch (IOException e) {
            if (this.useBufferPool) {
                BufferPool.INSTANCE.release(buffer);
            }
            throw e;
        }
    }
}
