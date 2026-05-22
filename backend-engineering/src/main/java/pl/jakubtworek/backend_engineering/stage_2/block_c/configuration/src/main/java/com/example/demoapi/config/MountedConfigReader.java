package pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.config;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class MountedConfigReader {

    private final AppProperties properties;

    public MountedConfigReader(AppProperties properties) {
        this.properties = properties;
    }

    public String readMountedConfig() throws IOException {
        // ConfigMap mounted as a volume can be updated eventually consistently by Kubernetes.
        // The application must read the file again if it wants to observe runtime changes.
        Path path = Path.of(properties.getMountedConfigPath());

        if (!Files.exists(path)) {
            return "<mounted config file does not exist>";
        }

        return Files.readString(path);
    }
}