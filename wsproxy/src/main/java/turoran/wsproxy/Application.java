package turoran.wsproxy;

import io.micronaut.runtime.Micronaut;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Application {
    public static void main(String[] args) {
        String configPath = System.getProperty("micronaut.config.files");
        if (configPath != null) {
            try (FileInputStream fis = new FileInputStream(configPath)) {
                Properties props = new Properties();
                props.load(fis);
                String port = props.getProperty("wsproxy.server.port");
                if (port != null) {
                    System.setProperty("micronaut.server.port", port);
                }
            } catch (IOException e) {
                // Ignore, let Micronaut handle it
            }
        }
        Micronaut.run(Application.class, args);
    }
}
