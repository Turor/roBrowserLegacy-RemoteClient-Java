/*
 * Copyright (C) 2026 turoran
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package turoran.robrowserclient;

import io.micronaut.runtime.Micronaut;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

@Slf4j
public class Application {
    public static void main(String[] args) {
        String configPath = System.getProperty("micronaut.config.files");
        if (configPath != null) {
            try (FileInputStream fis = new FileInputStream(configPath)) {
                Properties props = new Properties();
                props.load(fis);
                String port = props.getProperty("client.server.port");
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