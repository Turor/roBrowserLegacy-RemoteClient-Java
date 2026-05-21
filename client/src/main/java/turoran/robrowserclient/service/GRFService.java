package turoran.robrowserclient.service;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import turoran.grfloader.loader.FileResult;
import turoran.grfloader.loader.GRFNode;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.List;

@Slf4j
@Prototype
public class GRFService {

    private final String filePath;
    private final String fileName;
    private GRFNode grf;
    private boolean loaded = false;
    private RandomAccessFile fd;

    public GRFService(@Parameter String filePath) {
        this.filePath = filePath;
        this.fileName = new File(filePath).getName();
    }

    public synchronized void load() {
        File file = new File(this.filePath);
        if (!file.exists()) {
            log.error("GRF file not found: {}", this.filePath);
            return;
        }

        try {
            this.fd = new RandomAccessFile(file, "r");
            this.grf = new GRFNode(this.fd);
            this.grf.load();
            this.loaded = true;
        } catch (Exception error) {
            log.error("Error loading GRF file:", error);
            if (this.fd != null) {
                try {
                    this.fd.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    public byte[] getFile(String filename) {
        if (!this.loaded || this.grf == null) {
            log.error("GRF not loaded or not initialized");
            return null;
        }
        try {
            FileResult result = this.grf.getFile(filename);
            if (result.error != null) {
                return null;
            }
            return result.data;
        } catch (Exception error) {
            log.error("Error extracting file: {}", error.getMessage());
            return null;
        }
    }

    public List<String> listFiles() {
        if (!this.loaded || this.grf == null) {
            log.error("GRF not loaded or not initialized");
            return Collections.emptyList();
        }

        return this.grf.listFiles();
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void close() {
        if (this.fd != null) {
            try {
                this.fd.close();
            } catch (IOException e) {
                log.error("Error closing GRF file:", e);
            }
        }
    }
}
