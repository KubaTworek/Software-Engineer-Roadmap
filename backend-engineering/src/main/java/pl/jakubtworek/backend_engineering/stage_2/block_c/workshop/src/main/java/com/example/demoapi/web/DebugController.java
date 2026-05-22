package pl.jakubtworek.backend_engineering.stage_2.block_c.workshop.src.main.java.com.example.demoapi.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.jakubtworek.backend_engineering.stage_2.block_c.workshop.src.main.java.com.example.demoapi.debug.DebugInfoService;

import java.util.Map;

@RestController
public class DebugController {

    private final DebugInfoService debugInfoService;

    public DebugController(DebugInfoService debugInfoService) {
        this.debugInfoService = debugInfoService;
    }

    @GetMapping("/debug/info")
    public ResponseEntity<Map<String, Object>> debugInfo() {
        // This endpoint is intentionally limited to non-sensitive diagnostics.
        // In production, protect or remove it depending on your security model.
        return ResponseEntity.ok(debugInfoService.buildDebugInfo());
    }
}