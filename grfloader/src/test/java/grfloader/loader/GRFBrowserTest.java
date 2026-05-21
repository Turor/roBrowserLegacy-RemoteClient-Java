package turoran.robrowser.grfloader.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import turoran.robrowser.grfloader.loader.FileResult;
import turoran.robrowser.grfloader.loader.GRFBrowser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.*;

public class GRFBrowserTest {

    @TempDir
    Path tempDir;

    @Test
    public void testGRFBrowserLoad() throws IOException {
        File grfFile = tempDir.resolve("test.grf").toFile();
        prepareMockGRF(grfFile);

        GRFBrowser browser = new GRFBrowser(grfFile);
        browser.load();

        assertEquals(0x200, browser.version);
        assertEquals(1, browser.fileCount);
        assertTrue(browser.hasFile("test.txt"));

                FileResult res = browser.getFile("test.txt");
        assertNotNull(res.data);
        assertEquals("Hello GRF!", new String(res.data, StandardCharsets.US_ASCII));
    }

    private void prepareMockGRF(File file) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            // Header (46 bytes)
            ByteBuffer header = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN);
            header.put("Master of Magic".getBytes(StandardCharsets.US_ASCII));
            header.position(30);
            int tableOffset = 100;
            header.putInt(tableOffset - 46); // table_offset (relative to HEADER_SIZE)
            header.putInt(0); // seeds
            header.putInt(1 + 7); // filecount (1 real file + 7 reserved)
            header.putInt(0x200); // version at 42
            fos.write(header.array());

            // Padding to tableOffset
            fos.write(new byte[tableOffset - 46]);

            // File Table
            String filename = "test.txt";
            byte[] fileNameBytes = filename.getBytes(StandardCharsets.US_ASCII);
            int entryDataSize = 17;
            int realTableSize = fileNameBytes.length + 1 + entryDataSize;

            ByteBuffer tableData = ByteBuffer.allocate(realTableSize).order(ByteOrder.LITTLE_ENDIAN);
            tableData.put(fileNameBytes);
            tableData.put((byte) 0);
            tableData.putInt(10); // compSize
            tableData.putInt(10); // lenAligned
            tableData.putInt(10); // realSize
            tableData.put((byte) 0x01); // type (FILE)
            tableData.putInt(200); // offset (relative to HEADER_SIZE)

            byte[] tableBytes = tableData.array();
            byte[] compressedTable = compress(tableBytes);

            ByteBuffer tableHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            tableHeader.putInt(compressedTable.length);
            tableHeader.putInt(tableBytes.length);

            fos.write(tableHeader.array());
            fos.write(compressedTable);

            // File Data at offset 200 + 46 = 246
            // We need to write padding until 246
            long currentPos = tableOffset + 8 + compressedTable.length;
            int paddingSize = (int) (246 - currentPos);
            if (paddingSize > 0) {
                fos.write(new byte[paddingSize]);
            }

            byte[] fileData = "Hello GRF!".getBytes(StandardCharsets.US_ASCII);
            fos.write(fileData);
        }
    }

    private byte[] compress(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        byte[] buffer = new byte[data.length + 100];
        int size = deflater.deflate(buffer);
        byte[] result = new byte[size];
        System.arraycopy(buffer, 0, result, 0, size);
        deflater.end();
        return result;
    }
}
