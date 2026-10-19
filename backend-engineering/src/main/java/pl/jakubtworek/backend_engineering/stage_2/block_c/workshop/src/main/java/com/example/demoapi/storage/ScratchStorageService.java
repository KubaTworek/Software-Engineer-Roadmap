package com.example.demoapi.storage;

import com.example.demoapi.config.AppProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class ScratchStorageService {

    private final AppProperties properties;

    public ScratchStorageService(AppProperties properties) {
        this.properties = properties;
    }

    public Path write(String name, String content) throws IOException {
        Path directory = Path.of(properties.getScratchDirectory()).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        Path file = resolveInside(directory, name);
        Files.writeString(file, content);
        return file;
    }

    public String read(String name) throws IOException {
        Path file = resolveInside(
                Path.of(properties.getScratchDirectory()).toAbsolutePath().normalize(),
                name
        );
        return Files.exists(file) ? Files.readString(file) : "<missing>";
    }

    private static Path resolveInside(Path directory, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("file name must not be empty");
        }
        Path file = directory.resolve(name).normalize();
        if (!file.startsWith(directory)) {
            throw new IllegalArgumentException("file must stay inside scratch directory");
        }
        return file;
    }
}
