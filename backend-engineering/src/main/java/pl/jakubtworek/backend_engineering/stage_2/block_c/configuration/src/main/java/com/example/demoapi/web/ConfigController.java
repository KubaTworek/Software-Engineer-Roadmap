package pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.config.MountedConfigReader;

import java.io.IOException;
import java.util.Map;

@RestController
public class ConfigController {

    private final MountedConfigReader mountedConfigReader;

    public ConfigController(MountedConfigReader mountedConfigReader) {
        this.mountedConfigReader = mountedConfigReader;
    }

    @GetMapping("/config/mounted")
    public ResponseEntity<Map<String, String>> mountedConfig() throws IOException {
        // Mounted ConfigMap files may be updated eventually consistently.
        // This endpoint reads the file at request time to demonstrate runtime reload behavior.
        return ResponseEntity.ok(Map.of(
                "content", mountedConfigReader.readMountedConfig()
        ));
    }
}