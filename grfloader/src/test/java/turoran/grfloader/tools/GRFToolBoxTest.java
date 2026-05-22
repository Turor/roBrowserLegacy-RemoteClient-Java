package turoran.grfloader.tools;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

public class GRFToolBoxTest {

    @Test
    void testSingletonInstance() {
        assertNotNull(GRFToolBox.getInstance());
        assertSame(GRFToolBox.getInstance(), GRFToolBox.getInstance());
    }

    @Test
    void testValidateAllGRFSNoExit() {
        assertDoesNotThrow(() -> {
            try {
                GRFToolBox.getInstance().runValidateAllGRFS(new String[]{"non_existent_folder"});
            } catch (IllegalArgumentException e) {
                // Expected
            }
        });
    }

    @Test
    void testVerifyGRFNoExit() throws Exception {
        boolean result = GRFToolBox.getInstance().runVerifyGRF(new String[]{});
        assertFalse(result, "Should return false for invalid args instead of exiting");
    }

    @Test
    void testPathMappingNoExit() throws Exception {
        Path tempDir = Files.createTempDirectory("grf-toolbox-test");
        try {
            // Should fail because DATA.INI doesn't exist, but should NOT exit
            System.setProperty("resources.path", tempDir.toString());
            GRFToolBox.getInstance().runPathMapping(new String[]{"--output=" + tempDir.resolve("out.json")});
        } finally {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }
}
