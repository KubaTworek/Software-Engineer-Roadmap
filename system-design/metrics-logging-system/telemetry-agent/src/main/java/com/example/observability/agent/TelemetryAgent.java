package com.example.observability.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TelemetryAgent {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Config config;

    public TelemetryAgent(Config config) {
        this.config = config;
    }

    public static void main(String[] args) throws Exception {
        Config config = Config.from(args, System.getenv());
        System.out.printf("Starting telemetry-agent server=%s tenant=%s service=%s file=%s%n",
                config.serverUrl, config.tenantId, config.serviceName, config.logFile);
        TelemetryAgent agent = new TelemetryAgent(config);
        agent.start();
    }

    public void start() throws Exception {
        ensureFileExists(config.logFile);
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
        executor.scheduleWithFixedDelay(this::sendHeartbeatSafely, 0, config.heartbeatSeconds, TimeUnit.SECONDS);
        executor.submit(this::tailLoop);
        Thread.currentThread().join();
    }

    private void tailLoop() {
        long pointer = 0;
        while (true) {
            try (RandomAccessFile file = new RandomAccessFile(config.logFile.toFile(), "r")) {
                if (pointer > file.length()) pointer = 0;
                file.seek(pointer);
                String line;
                List<Map<String, Object>> batch = new ArrayList<>();
                while ((line = file.readLine()) != null) {
                    String decoded = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                    batch.add(parseLogLine(decoded));
                    if (batch.size() >= config.batchSize) {
                        sendLogs(batch);
                        batch.clear();
                    }
                }
                if (!batch.isEmpty()) sendLogs(batch);
                pointer = file.getFilePointer();
                Thread.sleep(config.pollMillis);
            } catch (Exception e) {
                System.err.println("tail error: " + e.getMessage());
                sleep(2000);
            }
        }
    }

    private Map<String, Object> parseLogLine(String line) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", Instant.now().toString());
        event.put("level", "INFO");
        event.put("service", config.serviceName);
        event.put("host", config.hostName);
        event.put("traceId", "");
        event.put("message", line);
        event.put("attributes", Map.of("agent", "java-file-tail"));

        String trimmed = line.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                Map<String, Object> json = mapper.readValue(trimmed, new TypeReference<>() {});
                event.put("level", String.valueOf(json.getOrDefault("level", "INFO")).toUpperCase());
                event.put("message", String.valueOf(json.getOrDefault("message", trimmed)));
                event.put("traceId", String.valueOf(json.getOrDefault("traceId", json.getOrDefault("trace_id", ""))));
                event.put("attributes", json);
            } catch (Exception ignored) {
                // Fall back to plaintext.
            }
        } else if (trimmed.contains("ERROR")) {
            event.put("level", "ERROR");
        } else if (trimmed.contains("WARN")) {
            event.put("level", "WARN");
        }
        return event;
    }

    private void sendLogs(List<Map<String, Object>> logs) throws Exception {
        Map<String, Object> payload = Map.of("tenantId", config.tenantId, "logs", logs);
        postJson(config.serverUrl + "/api/v1/ingest/logs", payload);
    }

    private void sendHeartbeatSafely() {
        try {
            Map<String, Object> sample = Map.of("timestamp", Instant.now().toString(), "value", 1.0);
            Map<String, Object> series = Map.of(
                    "name", "agent_heartbeat_total",
                    "labels", Map.of("service", config.serviceName, "host", config.hostName),
                    "samples", List.of(sample)
            );
            postJson(config.serverUrl + "/api/v1/ingest/metrics", Map.of("tenantId", config.tenantId, "series", List.of(series)));
        } catch (Exception e) {
            System.err.println("heartbeat error: " + e.getMessage());
        }
    }

    private void postJson(String url, Object payload) throws Exception {
        String body = mapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IOException("POST " + url + " failed: " + response.statusCode() + " " + response.body());
        }
    }

    private static void ensureFileExists(Path path) throws IOException {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        if (!Files.exists(path)) Files.createFile(path);
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static class Config {
        String serverUrl;
        String tenantId;
        String serviceName;
        String hostName;
        Path logFile;
        int batchSize;
        long pollMillis;
        long heartbeatSeconds;

        static Config from(String[] args, Map<String, String> env) throws Exception {
            Map<String, String> cli = parseArgs(args);
            Config c = new Config();
            c.serverUrl = value(cli, env, "server-url", "AGENT_SERVER_URL", "http://localhost:8080").replaceAll("/$", "");
            c.tenantId = value(cli, env, "tenant-id", "AGENT_TENANT_ID", "demo");
            c.serviceName = value(cli, env, "service-name", "AGENT_SERVICE_NAME", "demo-service");
            c.hostName = value(cli, env, "host-name", "AGENT_HOST_NAME", java.net.InetAddress.getLocalHost().getHostName());
            c.logFile = Path.of(value(cli, env, "log-file", "AGENT_LOG_FILE", "/tmp/demo-app.log"));
            c.batchSize = Integer.parseInt(value(cli, env, "batch-size", "AGENT_BATCH_SIZE", "100"));
            c.pollMillis = Long.parseLong(value(cli, env, "poll-millis", "AGENT_POLL_MILLIS", "1000"));
            c.heartbeatSeconds = Long.parseLong(value(cli, env, "heartbeat-seconds", "AGENT_HEARTBEAT_SECONDS", "10"));
            return c;
        }

        static Map<String, String> parseArgs(String[] args) {
            Map<String, String> map = new HashMap<>();
            for (String arg : args) {
                if (arg.startsWith("--") && arg.contains("=")) {
                    String[] parts = arg.substring(2).split("=", 2);
                    map.put(parts[0], parts[1]);
                }
            }
            return map;
        }

        static String value(Map<String, String> cli, Map<String, String> env, String cliKey, String envKey, String fallback) {
            if (cli.containsKey(cliKey)) return cli.get(cliKey);
            return env.getOrDefault(envKey, fallback);
        }
    }
}
