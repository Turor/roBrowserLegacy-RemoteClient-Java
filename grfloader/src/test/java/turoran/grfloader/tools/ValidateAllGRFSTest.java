package turoran.grfloader.tools;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ValidateAllGRFSTest {

    @Test
    void testExecuteShouldNotExitProcess() {
        // This test aims to see if calling execute with invalid folder throws exception instead of crashing the JVM
        try {
            assertThrows(IllegalArgumentException.class, () -> {
                ValidateAllGRFS.execute(new String[]{"non_existent_folder"});
            });
        } catch (SecurityException e) {
            fail("System.exit was called");
        }
    }
}
