package com.example.demoapi.config;

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
        // Reading on demand demonstrates that mounted ConfigMap files can change at runtime.
        Path path = Path.of(properties.getMountedConfigPath());
        return Files.exists(path) ? Files.readString(path) : "<mounted config file does not exist>";
    }
}
