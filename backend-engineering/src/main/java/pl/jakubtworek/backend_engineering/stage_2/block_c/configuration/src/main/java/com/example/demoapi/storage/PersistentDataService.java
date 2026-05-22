package pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.storage;

import org.springframework.stereotype.Service;
import pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.config.AppProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PersistentDataService {

    private final AppProperties properties;

    public PersistentDataService(AppProperties properties) {
        this.properties = properties;
    }

    public Path writePersistentFile(String name, String content) throws IOException {
        // This directory should be backed by a PersistentVolumeClaim.
        // Its lifecycle is independent from a single Pod lifecycle.
        Path directory = Path.of(properties.getPersistentDirectory());
        Files.createDirectories(directory);

        Path file = directory.resolve(name);
        Files.writeString(file, content);

        return file;
    }

    public String readPersistentFile(String name) throws IOException {
        // PVC-backed data may survive Pod recreation depending on reclaim policy and storage class.
        Path file = Path.of(properties.getPersistentDirectory()).resolve(name);

        if (!Files.exists(file)) {
            return "<missing>";
        }

        return Files.readString(file);
    }
}