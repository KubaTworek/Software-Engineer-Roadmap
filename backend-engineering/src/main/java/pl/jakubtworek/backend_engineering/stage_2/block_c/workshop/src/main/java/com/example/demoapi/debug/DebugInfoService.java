package pl.jakubtworek.backend_engineering.stage_2.block_c.workshop.src.main.java.com.example.demoapi.debug;

import org.springframework.stereotype.Service;
import pl.jakubtworek.backend_engineering.stage_2.block_c.workshop.src.main.java.com.example.demoapi.config.AppProperties;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

@Service
public class DebugInfoService {

    private final AppProperties properties;

    public DebugInfoService(AppProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> buildDebugInfo() {
        // This is safe, high-level diagnostic data useful during "works locally, fails in cluster" triage.
        // Avoid returning secrets, tokens, full environment variables, or request headers.
        return Map.of(
                "application", properties.getName(),
                "imageTag", properties.getImageTag(),
                "commitSha", properties.getCommitSha(),
                "bindAddress", properties.getBindAddress(),
                "port", properties.getPort(),
                "hostname", hostname(),
                "runtime", RuntimeDiagnostics.fromEnvironment(System.getenv())
        );
    }

    private String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            return "<unknown>";
        }
    }
}