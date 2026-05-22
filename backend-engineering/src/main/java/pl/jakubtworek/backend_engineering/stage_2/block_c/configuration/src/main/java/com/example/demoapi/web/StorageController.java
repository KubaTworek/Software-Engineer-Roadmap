package pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.storage.PersistentDataService;
import pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.storage.ScratchStorageService;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/storage")
public class StorageController {

    private final ScratchStorageService scratchStorage;
    private final PersistentDataService persistentData;

    public StorageController(
            ScratchStorageService scratchStorage,
            PersistentDataService persistentData
    ) {
        this.scratchStorage = scratchStorage;
        this.persistentData = persistentData;
    }

    @PostMapping("/scratch")
    public ResponseEntity<Map<String, String>> writeScratch() throws IOException {
        // This writes to emptyDir-backed storage.
        // The data is temporary and should not be treated as durable.
        var path = scratchStorage.writeScratchFile(
                "scratch.txt",
                "scratch written at " + Instant.now()
        );

        return ResponseEntity.ok(Map.of("path", path.toString()));
    }

    @GetMapping("/scratch")
    public ResponseEntity<Map<String, String>> readScratch() throws IOException {
        return ResponseEntity.ok(Map.of(
                "content", scratchStorage.readScratchFile("scratch.txt")
        ));
    }

    @PostMapping("/persistent")
    public ResponseEntity<Map<String, String>> writePersistent() throws IOException {
        // This writes to PVC-backed storage.
        // The data lifecycle is independent from the current Pod instance.
        var path = persistentData.writePersistentFile(
                "persistent.txt",
                "persistent written at " + Instant.now()
        );

        return ResponseEntity.ok(Map.of("path", path.toString()));
    }

    @GetMapping("/persistent")
    public ResponseEntity<Map<String, String>> readPersistent() throws IOException {
        return ResponseEntity.ok(Map.of(
                "content", persistentData.readPersistentFile("persistent.txt")
        ));
    }
}
