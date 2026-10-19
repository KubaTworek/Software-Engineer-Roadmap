package com.example.demoapi.web;

import com.example.demoapi.storage.PersistentDataService;
import com.example.demoapi.storage.ScratchStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/storage")
public class StorageController {

    private final ScratchStorageService scratchStorage;
    private final PersistentDataService persistentData;

    public StorageController(ScratchStorageService scratchStorage, PersistentDataService persistentData) {
        this.scratchStorage = scratchStorage;
        this.persistentData = persistentData;
    }

    @PostMapping("/scratch")
    public ResponseEntity<Map<String, String>> writeScratch() throws IOException {
        return ResponseEntity.ok(Map.of(
                "path",
                scratchStorage.write("scratch.txt", "scratch written at " + Instant.now()).toString()
        ));
    }

    @GetMapping("/scratch")
    public ResponseEntity<Map<String, String>> readScratch() throws IOException {
        return ResponseEntity.ok(Map.of("content", scratchStorage.read("scratch.txt")));
    }

    @PostMapping("/persistent")
    public ResponseEntity<Map<String, String>> writePersistent() throws IOException {
        return ResponseEntity.ok(Map.of(
                "path",
                persistentData.write("persistent.txt", "persistent written at " + Instant.now()).toString()
        ));
    }

    @GetMapping("/persistent")
    public ResponseEntity<Map<String, String>> readPersistent() throws IOException {
        return ResponseEntity.ok(Map.of("content", persistentData.read("persistent.txt")));
    }
}
