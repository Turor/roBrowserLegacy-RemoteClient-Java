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
package turoran.robrowserclient.service;

import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import turoran.grfloader.tools.PathMappingTool;
import turoran.grfloader.tools.ValidateAllGRFS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Singleton
public class PostStartupTaskRunner {

    @Value("${client.tasks.validate-grfs:false}")
    private boolean validateGrfs;

    @Value("${client.tasks.generate-mapping:false}")
    private boolean generateMapping;

    @Value("${client.tasks.mapping-output:path-mapping.json}")
    private String mappingOutput;

    @Value("${client.rootpath:.}")
    private String rootPath;

    @Value("${client.resourcespath:resources}")
    private String resourcesPath;

    @EventListener
    public void onStartup(StartupEvent event) {
        if (validateGrfs) {
            runGrfValidation();
        }
    }

    private void runGrfValidation() {
        log.info("Starting post-startup GRF validation in: {}/{}", rootPath, resourcesPath);
        try {
            Path folderPath = Paths.get(rootPath, resourcesPath);
            if (!Files.exists(folderPath)) {
                log.warn("GRF validation skipped: folder does not exist: {}", folderPath.toAbsolutePath());
                return;
            }
            ValidateAllGRFS.execute(new String[]{folderPath.toString()});
            log.info("GRF validation completed.");
        } catch (Exception e) {
            log.error("Failed to validate GRFs: {}", e.getMessage(), e);
        }
    }
}
