package turoran.grfloader.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import turoran.grfloader.loader.Decoder;
import turoran.grfloader.loader.PathMapping;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.*;

public class PathMappingToolTest {

    @TempDir
    Path tempDir;

    @Test
    void testMojibakeDetectionAndCorrection() {
        // "데이터" in CP949 is 0xB5 0xA5 0xC0 0xCC 0xC5 0xCD
        // In Windows-1252/LATIN1 it looks like "µ¥ÀÌÅÍ"
        byte[] cp949Bytes = new byte[]{(byte) 0xB5, (byte) 0xA5, (byte) 0xC0, (byte) 0xCC, (byte) 0xC5, (byte) 0xCD};
        String latin1Str = new String(cp949Bytes, StandardCharsets.ISO_8859_1);

        assertTrue(Decoder.isMojibake(latin1Str), "Should be detected as mojibake");
        String fixed = Decoder.fixMojibake(latin1Str);
        assertEquals("데이터", fixed, "Should be corrected to Korean");
    }

    @Test
    void testGenerateMapping() throws IOException {
        // 1. Create a fake GRF with mojibake filenames
        Path grfPath = tempDir.resolve("test.grf");
        createFakeGrf(grfPath, new String[]{"data\\µ¥ÀÌÅÍ.txt"}); // data\데이터.txt

        // 2. Create DATA.INI
        Path dataIniPath = tempDir.resolve("DATA.INI");
        Files.writeString(dataIniPath, "[data]\n0=test.grf", Charset.forName("CP949"));

        // 3. Generate mapping
        Path mappingPath = tempDir.resolve("path-mapping.json");
        PathMappingTool.generateMapping("[data]\n0=test.grf", tempDir, mappingPath);

        // 4. Verify output
        assertTrue(Files.exists(mappingPath), "Mapping file should be created");
        
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        PathMapping mapping = mapper.readValue(mappingPath.toFile(), PathMapping.class);

        assertNotNull(mapping.getGeneratedAt());
        assertEquals(1, mapping.getGrfs().size());
        assertEquals("test.grf", mapping.getGrfs().get(0).getFile());
        
        // Check if paths contains the mapping
        // We expect: "data\데이터.txt" -> "data\µ¥ÀÌÅÍ.txt"
        // Also normalized versions
        assertTrue(mapping.getPaths().containsKey("data\\데이터.txt"));
        assertEquals("data\\µ¥ÀÌÅÍ.txt", mapping.getPaths().get("data\\데이터.txt"));
        
        assertTrue(mapping.getPaths().containsKey("data/데이터.txt"));
        // Note: normalized paths are lowercased in PathMappingTool
        assertEquals("data/µ¥ÀÌÅÍ.txt".toLowerCase(), mapping.getPaths().get("data/데이터.txt"));
    }

    @Test
    void testMultipleGrfsAndDataIniParsing() throws IOException {
        // Create two GRFs
        Path grf1Path = tempDir.resolve("first.grf");
        createFakeGrf(grf1Path, new String[]{"data\\µ¥ÀÌÅÍ1.txt"});
        
        Path grf2Path = tempDir.resolve("second.grf");
        createFakeGrf(grf2Path, new String[]{"data\\µ¥ÀÌÅÍ2.txt"});

        // Create DATA.INI with both
        String dataIniContent = "[data]\n0=first.grf\n1=second.grf";
        Path mappingPath = tempDir.resolve("multi-mapping.json");
        
        PathMappingTool.generateMapping(dataIniContent, tempDir, mappingPath);

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        PathMapping mapping = mapper.readValue(mappingPath.toFile(), PathMapping.class);

        assertEquals(2, mapping.getGrfs().size());
        assertTrue(mapping.getPaths().containsKey("data\\데이터1.txt"));
        assertTrue(mapping.getPaths().containsKey("data\\데이터2.txt"));
    }

    @Test
    void testEmptyGrf() throws IOException {
        Path grfPath = tempDir.resolve("empty.grf");
        createFakeGrf(grfPath, new String[]{});

        Path mappingPath = tempDir.resolve("empty-mapping.json");
        PathMappingTool.generateMapping("[data]\n0=empty.grf", tempDir, mappingPath);

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        PathMapping mapping = mapper.readValue(mappingPath.toFile(), PathMapping.class);

        assertEquals(1, mapping.getGrfs().size());
        assertEquals(0, mapping.getGrfs().get(0).getTotalFiles());
        assertTrue(mapping.getPaths().isEmpty());
    }

    @Test
    void testNonMojibakeFiles() throws IOException {
        Path grfPath = tempDir.resolve("ascii.grf");
        createFakeGrf(grfPath, new String[]{"data\\test.txt", "textures\\wall.bmp"});

        Path mappingPath = tempDir.resolve("ascii-mapping.json");
        PathMappingTool.generateMapping("[data]\n0=ascii.grf", tempDir, mappingPath);

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        PathMapping mapping = mapper.readValue(mappingPath.toFile(), PathMapping.class);

        assertEquals(1, mapping.getGrfs().size());
        assertEquals(2, mapping.getGrfs().get(0).getTotalFiles());
        assertEquals(0, mapping.getGrfs().get(0).getMapped());
        assertTrue(mapping.getPaths().isEmpty());
    }

    private void createFakeGrf(Path path, String[] filenames) throws IOException {
        int tableOffset = 100;
        int fileCount = filenames.length;
        
        ByteBuffer header = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN);
        header.put("Master of Magic".getBytes(StandardCharsets.US_ASCII));
        header.position(30);
        header.putInt(tableOffset - 46);
        header.putInt(0);
        header.putInt(fileCount + 7);
        header.putInt(0x200);

        // Prepare table data
        int entryDataSize = 17;
        int totalTableSize = 0;
        for (String name : filenames) {
            totalTableSize += name.getBytes(StandardCharsets.ISO_8859_1).length + 1 + entryDataSize;
        }
        
        ByteBuffer tableData = ByteBuffer.allocate(totalTableSize).order(ByteOrder.LITTLE_ENDIAN);
        for (String name : filenames) {
            tableData.put(name.getBytes(StandardCharsets.ISO_8859_1));
            tableData.put((byte) 0);
            tableData.putInt(0); // compSize
            tableData.putInt(0); // lenAligned
            tableData.putInt(0); // realSize
            tableData.put((byte) 1); // type
            tableData.putInt(0); // offset
        }

        byte[] compressedTable = compress(tableData.array());
        
        ByteBuffer tableHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        tableHeader.putInt(compressedTable.length);
        tableHeader.putInt(tableData.array().length);

        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(path.toFile(), "rw")) {
            raf.write(header.array());
            raf.seek(tableOffset);
            raf.write(tableHeader.array());
            raf.write(compressedTable);
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
        return result;
    }
}
