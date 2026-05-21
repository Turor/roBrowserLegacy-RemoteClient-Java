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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turoran.grfloader.tools.PathMappingTool;

import java.util.Arrays;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--generate-mapping")) {
            try {
                PathMappingTool.main(args);
                logger.info("Path mapping generation completed.");
            } catch (Exception e) {
                logger.error("Failed to generate path mapping: {}", e.getMessage(), e);
            }
            if (!Arrays.asList(args).contains("--run-server")) {
                return;
            }
        }
        Micronaut.run(Application.class, args);
    }
}