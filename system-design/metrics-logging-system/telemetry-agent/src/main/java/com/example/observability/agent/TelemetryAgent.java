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

/**
 * Lekki agent telemetryczny uruchamiany obok aplikacji.
 *
 * Główne zadania:
 * 1. Czyta logi z lokalnego pliku podobnie jak `tail -f`.
 * 2. Grupuje logi w batche i wysyła je do telemetry-server.
 * 3. Wysyła cykliczną metrykę heartbeat, żeby backend widział, że agent żyje.
 * 4. Obsługuje konfigurację z CLI/env, dzięki czemu nadaje się do Dockera/Kubernetes.
 *
 * To nie jest pełny produkcyjny agent typu Fluent Bit / OpenTelemetry Collector.
 * To prosty agent demonstracyjny dla projektu Metrics / Logging System.
 */
public class TelemetryAgent {

    /**
     * ObjectMapper używany do:
     * - serializacji payloadów wysyłanych do backendu,
     * - parsowania logów JSON, jeśli linia loga wygląda jak JSON.
     *
     * JavaTimeModule pozwala poprawnie obsługiwać typy czasu z java.time.
     */
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * Wspólny klient HTTP używany do wysyłania logów i metryk.
     *
     * Timeout połączenia chroni agenta przed zawieszeniem się,
     * gdy telemetry-server jest niedostępny albo sieć ma problem.
     */
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Konfiguracja agenta: adres serwera, tenant, service name,
     * plik logów, batch size, heartbeat interval, API key.
     */
    private final Config config;

    public TelemetryAgent(Config config) {
        this.config = config;
    }

    /**
     * Punkt wejścia aplikacji.
     *
     * Konfiguracja jest pobierana z:
     * 1. argumentów CLI, np. --server-url=http://localhost:8080
     * 2. zmiennych środowiskowych,
     * 3. wartości domyślnych.
     *
     * CLI ma pierwszeństwo nad env.
     */
    public static void main(String[] args) throws Exception {
        Config config = Config.from(args, System.getenv());

        System.out.printf(
                "Starting telemetry-agent server=%s tenant=%s service=%s file=%s%n",
                config.serverUrl,
                config.tenantId,
                config.serviceName,
                config.logFile
        );

        TelemetryAgent agent = new TelemetryAgent(config);
        agent.start();
    }

    /**
     * Uruchamia dwa główne procesy agenta:
     *
     * 1. Heartbeat metrics:
     *    - działa cyklicznie co config.heartbeatSeconds,
     *    - wysyła metrykę agent_heartbeat_total.
     *
     * 2. Tail log file:
     *    - stale czyta nowe linie z pliku logów,
     *    - wysyła je batchami do backendu.
     *
     * Executor ma 2 wątki, bo heartbeat i tailowanie logów powinny działać niezależnie.
     */
    public void start() throws Exception {
        ensureFileExists(config.logFile);

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

        // Cykliczna metryka życia agenta.
        executor.scheduleWithFixedDelay(
                this::sendHeartbeatSafely,
                0,
                config.heartbeatSeconds,
                TimeUnit.SECONDS
        );

        // Główna pętla czytania logów z pliku.
        executor.submit(this::tailLoop);

        // Utrzymuje proces przy życiu.
        // Bez tego main thread zakończyłby się po wystartowaniu executorów.
        Thread.currentThread().join();
    }

    /**
     * Główna pętla tailowania pliku logów.
     *
     * Działanie:
     * 1. Otwiera plik logów.
     * 2. Przesuwa się do ostatnio zapamiętanej pozycji.
     * 3. Czyta tylko nowe linie.
     * 4. Parsuje każdą linię do formatu eventu telemetrycznego.
     * 5. Wysyła logi batchami.
     * 6. Zapamiętuje aktualną pozycję w pliku.
     *
     * Ważne:
     * - pointer jest trzymany w pamięci, więc po restarcie agent zaczyna od początku pliku.
     * - jeśli plik został skrócony albo zrotowany, pointer jest resetowany do 0.
     * - to prosta implementacja; produkcyjnie potrzebny byłby trwały offset.
     */
    private void tailLoop() {
        long pointer = 0;

        while (true) {
            try (RandomAccessFile file = new RandomAccessFile(config.logFile.toFile(), "r")) {

                // Obsługa sytuacji, gdy plik został wyczyszczony albo zrotowany.
                if (pointer > file.length()) {
                    pointer = 0;
                }

                // Kontynuujemy czytanie od ostatniej znanej pozycji.
                file.seek(pointer);

                String line;
                List<Map<String, Object>> batch = new ArrayList<>();

                while ((line = file.readLine()) != null) {

                    /*
                     * RandomAccessFile.readLine() czyta tekst jako ISO-8859-1.
                     * Konwertujemy go do UTF-8, żeby poprawnie obsłużyć standardowe logi aplikacyjne.
                     */
                    String decoded = new String(
                            line.getBytes(StandardCharsets.ISO_8859_1),
                            StandardCharsets.UTF_8
                    );

                    batch.add(parseLogLine(decoded));

                    // Po osiągnięciu batchSize wysyłamy paczkę logów do backendu.
                    if (batch.size() >= config.batchSize) {
                        sendLogs(batch);
                        batch.clear();
                    }
                }

                // Wysyłamy końcówkę batcha, nawet jeśli nie osiągnęła batchSize.
                if (!batch.isEmpty()) {
                    sendLogs(batch);
                }

                // Zapamiętujemy offset, od którego zaczniemy kolejną iterację.
                pointer = file.getFilePointer();

                // Krótka pauza, żeby nie mielić CPU, gdy nie ma nowych logów.
                Thread.sleep(config.pollMillis);

            } catch (Exception e) {
                /*
                 * Agent nie powinien kończyć procesu przy chwilowym błędzie:
                 * - backend może być chwilowo niedostępny,
                 * - plik może być tymczasowo zablokowany,
                 * - sieć może mieć timeout.
                 *
                 * Dlatego logujemy błąd i próbujemy dalej.
                 */
                System.err.println("tail error: " + e.getMessage());
                sleep(2000);
            }
        }
    }

    /**
     * Zamienia pojedynczą linię loga na ustandaryzowany event wysyłany do backendu.
     *
     * Obsługiwane przypadki:
     *
     * 1. Log tekstowy:
     *    - message = cała linia,
     *    - level domyślnie INFO,
     *    - jeśli linia zawiera ERROR/WARN, ustawiany jest odpowiedni level.
     *
     * 2. Log JSON:
     *    - próbuje wyciągnąć level, message, traceId / trace_id,
     *    - cały JSON trafia do attributes.
     *
     * Dzięki temu agent obsługuje zarówno proste logi tekstowe,
     * jak i bardziej strukturalne logi aplikacyjne.
     */
    private Map<String, Object> parseLogLine(String line) {
        Map<String, Object> event = new LinkedHashMap<>();

        // Domyślne pola wymagane przez backend logów.
        event.put("timestamp", Instant.now().toString());
        event.put("level", "INFO");
        event.put("service", config.serviceName);
        event.put("host", config.hostName);
        event.put("traceId", "");
        event.put("message", line);
        event.put("attributes", Map.of("agent", "java-file-tail"));

        String trimmed = line.trim();

        // Próba parsowania loga strukturalnego JSON.
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                Map<String, Object> json = mapper.readValue(
                        trimmed,
                        new TypeReference<>() {}
                );

                event.put(
                        "level",
                        String.valueOf(json.getOrDefault("level", "INFO")).toUpperCase()
                );

                event.put(
                        "message",
                        String.valueOf(json.getOrDefault("message", trimmed))
                );

                /*
                 * Obsługujemy oba popularne warianty nazwy:
                 * - traceId
                 * - trace_id
                 *
                 * To ważne dla korelacji logów z trace'ami w Fazie 3.
                 */
                event.put(
                        "traceId",
                        String.valueOf(json.getOrDefault(
                                "traceId",
                                json.getOrDefault("trace_id", "")
                        ))
                );

                // Cały oryginalny JSON zostaje zachowany jako attributes.
                event.put("attributes", json);

            } catch (Exception ignored) {
                /*
                 * Jeśli linia wygląda jak JSON, ale nie da się jej sparsować,
                 * nie odrzucamy loga. Zostaje wysłany jako plaintext.
                 */
            }
        } else if (trimmed.contains("ERROR")) {
            event.put("level", "ERROR");
        } else if (trimmed.contains("WARN")) {
            event.put("level", "WARN");
        }
        return event;
    }

    /**
     * Wysyła batch logów do telemetry-server.
     *
     * Payload ma format zgodny z endpointem:
     *
     * POST /api/v1/ingest/logs
     *
     * tenantId jest dodawany na poziomie paczki,
     * a same eventy logów są przekazywane w polu logs.
     */
    private void sendLogs(List<Map<String, Object>> logs) throws Exception {
        Map<String, Object> payload = Map.of(
                "tenantId", config.tenantId,
                "logs", logs
        );

        postJson(config.serverUrl + "/api/v1/ingest/logs", payload);
    }

    /**
     * Wysyła metrykę heartbeat agenta.
     *
     * Cel:
     * - backend widzi, że agent działa,
     * - można monitorować brak heartbeatów jako problem z agentem,
     * - metryka może być użyta w dashboardzie i alertach.
     *
     * Metryka:
     * agent_heartbeat_total{service=..., host=...} = 1
     *
     * Metoda łapie wyjątki lokalnie, żeby problem z heartbeatem
     * nie zatrzymał tailowania logów.
     */
    private void sendHeartbeatSafely() {
        try {
            Map<String, Object> sample = Map.of(
                    "timestamp", Instant.now().toString(),
                    "value", 1.0
            );

            Map<String, Object> series = Map.of(
                    "name", "agent_heartbeat_total",
                    "labels", Map.of(
                            "service", config.serviceName,
                            "host", config.hostName
                    ),
                    "samples", List.of(sample)
            );

            postJson(
                    config.serverUrl + "/api/v1/ingest/metrics",
                    Map.of(
                            "tenantId", config.tenantId,
                            "series", List.of(series)
                    )
            );

        } catch (Exception e) {
            System.err.println("heartbeat error: " + e.getMessage());
        }
    }

    /**
     * Wspólna metoda do wysyłania JSON-a do backendu.
     *
     * Robi trzy kluczowe rzeczy:
     * 1. Serializuje payload do JSON.
     * 2. Dodaje nagłówek X-API-Key wymagany przez RBAC/auth backendu.
     * 3. Traktuje każdy status HTTP >= 300 jako błąd.
     *
     * Dzięki temu błędy autoryzacji, quoty albo problemy backendu
     * są widoczne w logach agenta.
     */
    private void postJson(String url, Object payload) throws Exception {
        String body = mapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-API-Key", config.apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() >= 300) {
            throw new IOException(
                    "POST " + url + " failed: "
                            + response.statusCode()
                            + " "
                            + response.body()
            );
        }
    }

    /**
     * Zapewnia, że plik logów istnieje.
     *
     * Jeśli katalog nadrzędny nie istnieje, zostanie utworzony.
     * Jeśli sam plik nie istnieje, agent go utworzy.
     *
     * Dzięki temu agent może wystartować nawet zanim aplikacja
     * zacznie faktycznie pisać logi.
     */
    private static void ensureFileExists(Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        if (!Files.exists(path)) {
            Files.createFile(path);
        }
    }

    /**
     * Pomocniczy sleep odporny na InterruptedException.
     *
     * Jeśli wątek zostanie przerwany, przywracamy flagę interrupt,
     * żeby nie zgubić sygnału zatrzymania.
     */
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Konfiguracja agenta.
     *
     * Wartości mogą pochodzić z:
     * - argumentów CLI,
     * - zmiennych środowiskowych,
     * - fallbacków domyślnych.
     *
     * To pozwala używać tego samego artefaktu lokalnie, w Dockerze i w Kubernetes.
     */
    static class Config {
        String serverUrl;
        String tenantId;
        String serviceName;
        String hostName;
        Path logFile;
        int batchSize;
        long pollMillis;
        long heartbeatSeconds;
        String apiKey;

        /**
         * Buduje konfigurację z CLI/env/defaultów.
         *
         * Priorytet:
         * 1. CLI, np. --tenant-id=demo
         * 2. ENV, np. AGENT_TENANT_ID=demo
         * 3. fallback zapisany w kodzie
         */
        static Config from(String[] args, Map<String, String> env) throws Exception {
            Map<String, String> cli = parseArgs(args);

            Config c = new Config();

            c.serverUrl = value(
                    cli,
                    env,
                    "server-url",
                    "AGENT_SERVER_URL",
                    "http://localhost:8080"
            ).replaceAll("/$", "");

            c.tenantId = value(
                    cli,
                    env,
                    "tenant-id",
                    "AGENT_TENANT_ID",
                    "demo"
            );

            c.serviceName = value(
                    cli,
                    env,
                    "service-name",
                    "AGENT_SERVICE_NAME",
                    "demo-service"
            );

            c.hostName = value(
                    cli,
                    env,
                    "host-name",
                    "AGENT_HOST_NAME",
                    java.net.InetAddress.getLocalHost().getHostName()
            );

            c.logFile = Path.of(value(
                    cli,
                    env,
                    "log-file",
                    "AGENT_LOG_FILE",
                    "/tmp/demo-app.log"
            ));

            c.batchSize = Integer.parseInt(value(
                    cli,
                    env,
                    "batch-size",
                    "AGENT_BATCH_SIZE",
                    "100"
            ));

            c.pollMillis = Long.parseLong(value(
                    cli,
                    env,
                    "poll-millis",
                    "AGENT_POLL_MILLIS",
                    "1000"
            ));

            c.heartbeatSeconds = Long.parseLong(value(
                    cli,
                    env,
                    "heartbeat-seconds",
                    "AGENT_HEARTBEAT_SECONDS",
                    "10"
            ));

            c.apiKey = value(
                    cli,
                    env,
                    "api-key",
                    "AGENT_API_KEY",
                    "demo-writer-key"
            );

            return c;
        }

        /**
         * Parsuje argumenty CLI w formacie:
         *
         * --key=value
         *
         * Przykład:
         * --server-url=http://localhost:8080
         * --tenant-id=demo
         */
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

        /**
         * Pobiera wartość konfiguracyjną według priorytetu:
         *
         * 1. CLI
         * 2. ENV
         * 3. fallback
         */
        static String value(
                Map<String, String> cli,
                Map<String, String> env,
                String cliKey,
                String envKey,
                String fallback
        ) {
            if (cli.containsKey(cliKey)) {
                return cli.get(cliKey);
            }

            return env.getOrDefault(envKey, fallback);
        }
    }
}