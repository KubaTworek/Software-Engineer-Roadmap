package com.example.demoapi.web;

import com.example.demoapi.config.MountedConfigReader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
        return ResponseEntity.ok(Map.of("content", mountedConfigReader.readMountedConfig()));
    }
}
