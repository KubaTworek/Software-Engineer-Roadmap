package pl.jakubtworek.backend_engineering.stage_2.block_c.workshop.src.main.java.com.example.demoapi.debug;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.Map;

public record RuntimeDiagnostics(
        String timestamp,
        long pid,
        String jvmName,
        String javaVersion,
        String user,
        String workingDirectory,
        Map<String, String> selectedEnvironment
) {

    public static RuntimeDiagnostics fromEnvironment(Map<String, String> env) {
        // This object intentionally exposes only selected, non-sensitive values.
        // Never dump the full environment in production because it may contain secrets.
        return new RuntimeDiagnostics(
                Instant.now().toString(),
                ProcessHandle.current().pid(),
                ManagementFactory.getRuntimeMXBean().getName(),
                System.getProperty("java.version"),
                System.getProperty("user.name"),
                System.getProperty("user.dir"),
                Map.of(
                        "APP_NAME", env.getOrDefault("APP_NAME", "<missing>"),
                        "IMAGE_TAG", env.getOrDefault("IMAGE_TAG", "<missing>"),
                        "COMMIT_SHA", env.getOrDefault("COMMIT_SHA", "<missing>"),
                        "PORT", env.getOrDefault("PORT", "<missing>")
                )
        );
    }
}