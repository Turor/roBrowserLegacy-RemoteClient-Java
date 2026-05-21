package turoran.grfloader.loader;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class GRFResourcesTest {

    private File getResourceFile(String name) throws Exception {
        URL url = getClass().getClassLoader().getResource(name);
        assertNotNull(url, "Resource not found: " + name);
        return new File(url.toURI());
    }

    @Test
    public void testWithFiles() throws Exception {
        File file = getResourceFile("with-files.grf");
        GRFBrowser grf = new GRFBrowser(file);
        grf.load();

        assertTrue(grf.fileCount > 0);
        assertTrue(grf.hasFile("raw"));
        assertTrue(grf.hasFile("compressed"));

        FileResult resRaw = grf.getFile("raw");
        assertNotNull(resRaw.data);
        String rawContent = new String(resRaw.data, StandardCharsets.UTF_8).trim();
        assertTrue(rawContent.contains("test"), "Raw content should contain 'test'");

        FileResult resComp = grf.getFile("compressed");
        assertNotNull(resComp.data);
        String compContent = new String(resComp.data, StandardCharsets.UTF_8).trim();
        assertTrue(compContent.contains("test"), "Compressed content should contain 'test'");
    }

    @Test
    public void testWithFilesV300() throws Exception {
        File file = getResourceFile("with-files-v300.grf");
        GRFBrowser grf = new GRFBrowser(file);
        grf.load();

        assertEquals(0x300, grf.version);
        assertTrue(grf.fileCount > 0);
        assertTrue(grf.hasFile("raw"));
        assertTrue(grf.hasFile("compressed"));

        FileResult resRaw = grf.getFile("raw");
        assertNotNull(resRaw.data);
        String rawContent = new String(resRaw.data, StandardCharsets.UTF_8).trim();
        assertTrue(rawContent.contains("test"), "Raw content should contain 'test'");

        FileResult resComp = grf.getFile("compressed");
        assertNotNull(resComp.data);
        String compContent = new String(resComp.data, StandardCharsets.UTF_8).trim();
        assertTrue(compContent.contains("test"), "Compressed content should contain 'test'");
    }

    @Test
    public void testNotGrf() throws Exception {
        File file = getResourceFile("not-grf.grf");
        GRFBrowser grf = new GRFBrowser(file);
        
        GrfError error = assertThrows(GrfError.class, grf::load);
        assertEquals(GrfErrorCode.INVALID_MAGIC, error.code);
    }

    @Test
    public void testIncorrectVersion() throws Exception {
        File file = getResourceFile("incorrect-version.grf");
        GRFBrowser grf = new GRFBrowser(file);
        
        GrfError error = assertThrows(GrfError.class, grf::load);
        assertEquals(GrfErrorCode.UNSUPPORTED_VERSION, error.code);
    }

    @Test
    public void testCorrupted() throws Exception {
        File file = getResourceFile("corrupted.grf");
        GRFBrowser grf = new GRFBrowser(file);
        
        // Corrupted GRF might fail at load or when getting file
        // Usually load() parses the file table
        assertThrows(Exception.class, grf::load);
    }
}
