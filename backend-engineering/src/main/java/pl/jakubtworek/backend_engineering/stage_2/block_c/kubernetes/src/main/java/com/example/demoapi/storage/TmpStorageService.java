package pl.jakubtworek.backend_engineering.stage_2.block_c.kubernetes.src.main.java.com.example.demoapi.storage;

import org.springframework.stereotype.Service;
import pl.jakubtworek.backend_engineering.stage_2.block_c.kubernetes.src.main.java.com.example.demoapi.config.AppProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class TmpStorageService {

    private final AppProperties properties;

    public TmpStorageService(AppProperties properties) {
        this.properties = properties;
    }

    public Path createScratchFile(String fileName, String content) throws IOException {
        // This method intentionally writes only to temporary scratch space.
        // Data stored here must be treated as disposable because emptyDir follows the Pod lifecycle.
        Path directory = Path.of(properties.getTmpDirectory());
        Files.createDirectories(directory);

        Path file = directory.resolve(fileName);
        Files.writeString(file, content);

        return file;
    }
}