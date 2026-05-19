package turoran.grfloader.loader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Deflater;

public class GRFBaseTest {

    private static class TestGRF extends GRFBase<Map<Long, byte[]>> {
        public TestGRF(Map<Long, byte[]> fd) {
            super(fd);
        }

        public TestGRF(Map<Long, byte[]> fd, GrfOptions options) {
            super(fd, options);
        }

        @Override
        public byte[] getStreamBuffer(Map<Long, byte[]> fd, long offset, int length) throws IOException {
            byte[] data = fd.get(offset);
            if (data == null) throw new IOException("Offset " + offset + " not found");
            if (data.length != length) {
                // Return exactly length if it's stored differently
                byte[] sub = new byte[length];
                System.arraycopy(data, 0, sub, 0, Math.min(data.length, length));
                return sub;
            }
            return data;
        }
    }

    @Test
    public void testParseHeaderAndFileList() throws Exception {
        Map<Long, byte[]> mockData = new HashMap<>();
        
        // Prepare mock GRF content (0x200)
        ByteBuffer header = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN);
        header.put("Master of Magic".getBytes(StandardCharsets.US_ASCII));
        header.position(30);
        header.put(new byte[12]); // seeds/padding
        header.putInt(0x200); // version at 42
        
        // Re-read for version specific fields
        header.position(30);
        int tableOffset = 100;
        header.putInt(tableOffset - 46); // table_offset (relative to HEADER_SIZE)
        header.putInt(0); // seeds
        header.putInt(1 + 7); // filecount (1 real file + 7 reserved)
        
        mockData.put(0L, header.array());

        // Prepare File Table
        // [compressedSize:u32][realSize:u32]
        // Entry: [filename\0][compressedSize:u32][lenAligned:u32][realSize:u32][type:u8][offset:u32]
        
        String filename = "test.txt";
        byte[] fileNameBytes = filename.getBytes(StandardCharsets.US_ASCII);
        int entryDataSize = 17;
        int realTableSize = fileNameBytes.length + 1 + entryDataSize;
        
        ByteBuffer tableData = ByteBuffer.allocate(realTableSize).order(ByteOrder.LITTLE_ENDIAN);
        tableData.put(fileNameBytes);
        tableData.put((byte)0);
        tableData.putInt(10); // compSize
        tableData.putInt(10); // lenAligned
        tableData.putInt(10); // realSize
        tableData.put((byte)0x01); // type (FILE)
        tableData.putInt(200); // offset (relative to HEADER_SIZE)
        
        byte[] tableBytes = tableData.array();
        
        // Zlib compress table
        byte[] compressedTable = compress(tableBytes);
        
        ByteBuffer tableHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        tableHeader.putInt(compressedTable.length);
        tableHeader.putInt(tableBytes.length);
        
        mockData.put((long)tableOffset, tableHeader.array());
        mockData.put((long)tableOffset + 8, compressedTable);
        
        // Prepare File Data
        byte[] fileData = "Hello GRF!".getBytes(StandardCharsets.US_ASCII);
        mockData.put(200L + 46L, fileData);

        TestGRF grf = new TestGRF(mockData);
        grf.load();
        
        assertEquals(0x200, grf.version);
        assertEquals(1, grf.fileCount);
        assertTrue(grf.hasFile("test.txt"));
        
                FileResult res = grf.getFile("test.txt");
        assertNotNull(res.data);
        assertEquals("Hello GRF!", new String(res.data, StandardCharsets.US_ASCII));
    }

    private byte[] compress(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        byte[] buffer = new byte[data.length + 100];
        int size = deflater.deflate(buffer);
        byte[] result = new byte[size];
        System.arraycopy(buffer, 0, result, 0, size);
        return result;
    }
}
