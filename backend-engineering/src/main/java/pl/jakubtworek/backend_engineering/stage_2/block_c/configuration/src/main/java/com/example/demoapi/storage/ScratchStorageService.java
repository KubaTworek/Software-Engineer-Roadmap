package pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.storage;

import org.springframework.stereotype.Service;
import pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.config.AppProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class ScratchStorageService {

    private final AppProperties properties;

    public ScratchStorageService(AppProperties properties) {
        this.properties = properties;
    }

    public Path writeScratchFile(String name, String content) throws IOException {
        // This directory should be backed by emptyDir.
        // Data stored here is disposable and will disappear when the Pod disappears.
        Path directory = Path.of(properties.getScratchDirectory());
        Files.createDirectories(directory);

        Path file = directory.resolve(name);
        Files.writeString(file, content);

        return file;
    }

    public String readScratchFile(String name) throws IOException {
        // emptyDir survives container restarts inside the same Pod,
        // but it does not survive Pod deletion or rescheduling.
        Path file = Path.of(properties.getScratchDirectory()).resolve(name);

        if (!Files.exists(file)) {
            return "<missing>";
        }

        return Files.readString(file);
    }
}