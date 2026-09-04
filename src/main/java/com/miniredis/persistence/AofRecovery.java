package com.miniredis.persistence;

import com.miniredis.store.KeyValueStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AofRecovery {

    /**
     * Reads the AOF file line-by-line and replays each command directly
     * into the store, WITHOUT re-triggering AofLogger.log() calls.
     */
    public static void recover(KeyValueStore store, Path logPath) {
        if (!Files.exists(logPath)) {
            System.out.println("[AofRecovery] No AOF file found, starting fresh.");
            return;
        }

        int replayed = 0;
        int skipped = 0;

        try (BufferedReader reader = Files.newBufferedReader(logPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                boolean ok = applyLine(store, line);
                if (ok) {
                    replayed++;
                } else {
                    skipped++;
                    System.err.println("[AofRecovery] Skipped malformed line: " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("[AofRecovery] Failed to read AOF file: " + e.getMessage());
        }

        System.out.printf("[AofRecovery] Recovery complete. Replayed %d commands, skipped %d.%n",
                replayed, skipped);
    }

    private static boolean applyLine(KeyValueStore store, String line) {
        String[] parts = line.split(" ", 4); // limit 4 in case value itself has spaces at the end

        if (parts.length == 0) return false;

        String command = parts[0];

        switch (command) {
            case "SET" -> {
                if (parts.length < 4) return false;
                String key = parts[1];
                String value = parts[2];
                long expireAt;
                try {
                    expireAt = Long.parseLong(parts[3]);
                } catch (NumberFormatException e) {
                    return false;
                }
                store.setAbsolute(key, value, expireAt);
                return true;
            }
            case "DEL" -> {
                if (parts.length < 2) return false;
                store.delSilent(parts[1]);
                return true;
            }
            case "EXPIRE" -> {
                if (parts.length < 3) return false;
                String key = parts[1];
                long expireAt;
                try {
                    expireAt = Long.parseLong(parts[2]);
                } catch (NumberFormatException e) {
                    return false;
                }
                // Reuse setAbsolute-style update: fetch current value, reapply with new expiry.
                // Simpler: just call get() bypassing expiry check isn't available, so we
                // directly manipulate via store view for recovery purposes.
                var current = store.getStoreView().get(key);
                if (current != null) {
                    store.setAbsolute(key, current.value(), expireAt);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
