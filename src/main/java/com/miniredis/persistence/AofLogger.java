package com.miniredis.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class AofLogger {

    private final Path logPath;

    public AofLogger(String filePath) {
        this.logPath = Path.of(filePath);
        try {
            if (!Files.exists(logPath)) {
                Files.createFile(logPath);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to initialize AOF file", e);
        }
    }

    /**
     * Appends a single command line to the AOF file.
     * Synchronized because multiple client threads may call this concurrently.
     * Flushes immediately so the write survives even if the process is killed
     * right after this call returns.
     */
    public synchronized void log(String commandLine) {
        try {
            Files.writeString(
                    logPath,
                    commandLine + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            // In a production system you might crash-loop or alert here.
            // For our purposes, log the failure so it's visible during dev.
            System.err.println("[AofLogger] Failed to write to AOF: " + e.getMessage());
        }
    }

    public Path getLogPath() {
        return logPath;
    }
}
