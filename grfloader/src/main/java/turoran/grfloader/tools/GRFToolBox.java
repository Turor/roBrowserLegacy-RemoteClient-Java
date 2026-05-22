package turoran.grfloader.tools;

import lombok.extern.slf4j.Slf4j;
import java.io.IOException;

/**
 * GRFToolBox is a singleton class that provides access to GRF tools
 * without calling System.exit(), making them safe to call from within a running JVM.
 */
@Slf4j
public class GRFToolBox {

    private static final GRFToolBox INSTANCE = new GRFToolBox();

    private GRFToolBox() {}

    public static GRFToolBox getInstance() {
        return INSTANCE;
    }

    /**
     * Runs the PathMappingTool.
     * @param args Command line arguments
     * @throws IOException If an I/O error occurs
     */
    public void runPathMapping(String[] args) throws IOException {
        log.info("Running PathMappingTool via GRFToolBox");
        PathMappingTool.execute(args);
    }

    /**
     * Runs the ValidateAllGRFS tool.
     * @param args Command line arguments
     */
    public void runValidateAllGRFS(String[] args) {
        log.info("Running ValidateAllGRFS via GRFToolBox");
        ValidateAllGRFS.execute(args);
    }

    /**
     * Runs the VerifyGRF tool.
     * @param args Command line arguments
     * @return true if all tests passed, false otherwise
     * @throws Exception If an error occurs
     */
    public boolean runVerifyGRF(String[] args) throws Exception {
        log.info("Running VerifyGRF via GRFToolBox");
        return VerifyGRF.execute(args);
    }
}
