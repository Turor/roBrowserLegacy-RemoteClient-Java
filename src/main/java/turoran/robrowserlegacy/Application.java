package turoran.robrowserlegacy;

import io.micronaut.runtime.Micronaut;
import turoran.grfloader.tools.PathMappingTool;

import java.util.Arrays;

public class Application {

    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--generate-mapping")) {
            try {
                PathMappingTool.main(args);
                System.out.println("Path mapping generation completed.");
            } catch (Exception e) {
                System.err.println("Failed to generate path mapping: " + e.getMessage());
            }
            if (!Arrays.asList(args).contains("--run-server")) {
                return;
            }
        }
        Micronaut.run(Application.class, args);
    }
}