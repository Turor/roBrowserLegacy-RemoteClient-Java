package turoran.robrowser.client.service;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GRFServiceTest {

    private File getResourceFile(String name) {
        File file = new File("../grfloader/src/test/resources/" + name);
        if (!file.exists()) {
            throw new RuntimeException("Resource not found: " + file.getAbsolutePath());
        }
        return file;
    }

    @Test
    public void testGRFController() {
        File file = getResourceFile("with-files.grf");
        GRFService controller = new GRFService(file.getAbsolutePath());
        
        assertFalse(controller.isLoaded());
        assertEquals("with-files.grf", controller.getFileName());

        controller.load();
        assertTrue(controller.isLoaded());

        List<String> files = controller.listFiles();
        assertNotNull(files);
        assertTrue(files.contains("raw"));
        assertTrue(files.contains("compressed"));

        byte[] rawData = controller.getFile("raw");
        assertNotNull(rawData);
        String rawContent = new String(rawData, StandardCharsets.UTF_8).trim();
        assertTrue(rawContent.contains("test"));

        byte[] compData = controller.getFile("compressed");
        assertNotNull(compData);
        String compContent = new String(compData, StandardCharsets.UTF_8).trim();
        assertTrue(compContent.contains("test"));

        controller.close();
    }

    @Test
    public void testFileNotFound() {
        GRFService controller = new GRFService("non_existent_file.grf");
        controller.load();
        assertFalse(controller.isLoaded());
        assertNull(controller.getFile("any"));
        assertTrue(controller.listFiles().isEmpty());
    }
}
