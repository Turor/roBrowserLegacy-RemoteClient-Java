package turoran.robrowser.grfloader.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import turoran.robrowser.grfloader.loader.GRFNode;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class GRFNodeTest {

    @TempDir
    Path tempDir;

    @Test
    public void testGetStreamBuffer() throws IOException {
        File tempFile = tempDir.resolve("test.grf").toFile();
        byte[] expectedData = "Some GRF data content".getBytes();
        
        try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw")) {
            raf.write(expectedData);
        }

        try (RandomAccessFile raf = new RandomAccessFile(tempFile, "r")) {
            GRFNode grfNode = new GRFNode(raf);
            
            // Test reading full content
            byte[] data = grfNode.getStreamBuffer(raf, 0, expectedData.length);
            assertArrayEquals(expectedData, data);
            
            // Test reading sub-portion
            byte[] subData = grfNode.getStreamBuffer(raf, 5, 3);
            assertEquals("GRF", new String(subData, 0, 3));
        }
    }

    @Test
    public void testInvalidLength() throws IOException {
        File tempFile = tempDir.resolve("test_invalid.grf").toFile();
        try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw")) {
            raf.write("short".getBytes());
        }

        try (RandomAccessFile raf = new RandomAccessFile(tempFile, "r")) {
            GRFNode grfNode = new GRFNode(raf);
            
            // Requesting more data than available should throw IOException in our implementation
            assertThrows(IOException.class, () -> {
                grfNode.getStreamBuffer(raf, 0, 10);
            });
        }
    }

    @Test
    public void testInvalidFD() throws IOException {
        // We can't easily create an invalid FD without closing it, but let's try passing null
        assertThrows(Error.class, () -> {
            new GRFNode(null);
        });
    }
}
