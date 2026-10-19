package pl.jakubtworek.marketplace.integrationtest;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;
import tools.jackson.databind.json.JsonMapper;
import pl.jakubtworek.marketplace.integration.cdc.DebeziumOrderChangeAdapter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pełny kontrakt infrastruktury, a nie ręcznie spreparowany fake: PostgreSQL WAL
 * jest czytany przez Debezium Connect, rekord trafia do Kafki, a adapter aplikacji
 * mapuje rzeczywisty envelope do deterministycznego pipeline'u projekcji.
 */
@Tag("cdc")
class DebeziumCdcIntegrationTest {
    private static final Network NETWORK = Network.newNetwork();
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withNetwork(NETWORK).withNetworkAliases("postgres")
            .withCommand("postgres", "-c", "wal_level=logical");
    private static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.8.0"))
            .withNetwork(NETWORK).withNetworkAliases("kafka");
    private static final GenericContainer<?> CONNECT = new GenericContainer<>(
            DockerImageName.parse("quay.io/debezium/connect:3.3"))
            .withNetwork(NETWORK).withExposedPorts(8083)
            .withEnv("BOOTSTRAP_SERVERS", "kafka:9093")
            .withEnv("GROUP_ID", "marketplace-cdc-connect")
            .withEnv("CONFIG_STORAGE_TOPIC", "marketplace-connect-config")
            .withEnv("OFFSET_STORAGE_TOPIC", "marketplace-connect-offset")
            .withEnv("STATUS_STORAGE_TOPIC", "marketplace-connect-status")
            // Port otwiera się wcześniej niż kontekst REST /connectors. Czekanie
            // wyłącznie na socket dawałoby deterministyczny 404 podczas rejestracji.
            .waitingFor(Wait.forHttp("/connectors").forStatusCode(200)
                    .withStartupTimeout(Duration.ofSeconds(90)));

    private static UUID snapshotOrder;

    @BeforeAll static void startPipeline() throws Exception {
        POSTGRES.start();
        KAFKA.start();
        snapshotOrder = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE public.cdc_orders(id UUID PRIMARY KEY, status VARCHAR(32) NOT NULL)");
            statement.execute("INSERT INTO public.cdc_orders VALUES ('" + snapshotOrder + "', 'PENDING')");
        }
        CONNECT.start();
        CONNECT.followOutput(frame -> { });
        registerConnector();
    }

    @AfterAll static void stopPipeline() {
        CONNECT.stop(); KAFKA.stop(); POSTGRES.stop(); NETWORK.close();
    }

    @Test void capturesInitialSnapshotAndThenLiveUpdate() throws Exception {
        var adapter = new DebeziumOrderChangeAdapter(new JsonMapper());
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "cdc-proof-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of("marketplace.public.cdc_orders"));
            var snapshot = awaitRecord(consumer, "r");
            assertThat(adapter.fromKafka(snapshot.partition(), snapshot.offset(), snapshot.value()).value().orderId())
                    .isEqualTo(snapshotOrder);

            try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("UPDATE public.cdc_orders SET status='CONFIRMED' WHERE id='" + snapshotOrder + "'");
            }
            var update = awaitRecord(consumer, "u");
            assertThat(adapter.fromKafka(update.partition(), update.offset(), update.value()).value().status())
                    .isEqualTo("CONFIRMED");
        }
    }

    private static org.apache.kafka.clients.consumer.ConsumerRecord<String, String> awaitRecord(
            KafkaConsumer<String, String> consumer, String operation) {
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        while (System.nanoTime() < deadline) {
            for (var record : consumer.poll(Duration.ofMillis(500))) {
                if (record.value() != null && record.value().contains("\"op\":\"" + operation + "\"")) return record;
            }
        }
        throw new AssertionError("Debezium record with operation " + operation + " not received");
    }

    private static void registerConnector() throws Exception {
        String body = """
                {"name":"marketplace-postgres","config":{
                  "connector.class":"io.debezium.connector.postgresql.PostgresConnector",
                  "database.hostname":"postgres","database.port":"5432",
                  "database.user":"%s","database.password":"%s","database.dbname":"%s",
                  "topic.prefix":"marketplace","plugin.name":"pgoutput",
                  "table.include.list":"public.cdc_orders","snapshot.mode":"initial",
                  "key.converter":"org.apache.kafka.connect.json.JsonConverter",
                  "value.converter":"org.apache.kafka.connect.json.JsonConverter",
                  "key.converter.schemas.enable":"false","value.converter.schemas.enable":"false"
                }}
                """.formatted(POSTGRES.getUsername(), POSTGRES.getPassword(), POSTGRES.getDatabaseName());
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                .uri(URI.create("http://" + CONNECT.getHost() + ":" + CONNECT.getMappedPort(8083) + "/connectors"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("Cannot register Debezium: " + response.body());
    }
}
