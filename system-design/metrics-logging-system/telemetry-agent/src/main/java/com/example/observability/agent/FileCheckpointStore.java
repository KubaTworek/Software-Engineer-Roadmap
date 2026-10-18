package com.example.observability.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Trwały offset jest granicą gwarancji at-least-once agenta. Offset jest
 * przesuwany dopiero po odpowiedzi 2xx z ingest API. Zapis przez plik
 * tymczasowy zapobiega pozostawieniu pustego checkpointu po awarii procesu.
 */
final class FileCheckpointStore {
    private final Path path;

    FileCheckpointStore(Path path) {
        this.path = path.toAbsolutePath().normalize();
    }

    long load() {
        try {
            if (!Files.exists(path)) return 0;
            return Math.max(0, Long.parseLong(Files.readString(path).trim()));
        } catch (Exception ignored) {
            // Uszkodzony checkpoint oznacza replay od początku. Stabilne eventId
            // sprawia, że replay nie zmienia logicznego wyniku w storage'u.
            return 0;
        }
    }

    void save(long offset) throws IOException {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, Long.toString(offset), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
