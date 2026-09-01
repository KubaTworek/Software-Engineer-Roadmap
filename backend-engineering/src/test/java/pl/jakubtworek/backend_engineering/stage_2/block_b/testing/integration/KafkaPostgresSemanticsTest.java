package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.integration;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Executable specification of Kafka's at-least-once semantics together with a
 * PostgreSQL transaction boundary. The suite deliberately uses the low-level
 * Kafka client: partitions, offsets and commits should remain visible while
 * learning these guarantees.
 */
@Tag("infrastructure")
@Testcontainers
class KafkaPostgresSemanticsTest {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(10);
    private static final String ORIGINAL_TOPIC = "x-original-topic";
    private static final String ORIGINAL_PARTITION = "x-original-partition";
    private static final String ORIGINAL_OFFSET = "x-original-offset";
    private static final String RETRY_COUNT = "x-retry-count";
    private static final String ERROR_CLASS = "x-error-class";
    private static final String ERROR_MESSAGE = "x-error-message";

    @Container
    private static final KafkaContainer KAFKA =
            new KafkaContainer("apache/kafka-native:3.8.0");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void createSchema() throws SQLException {
        try (Connection connection = databaseConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS payments (
                        order_id VARCHAR(100) PRIMARY KEY,
                        handled_events INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS processed_events (
                        event_id VARCHAR(100) PRIMARY KEY,
                        processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS outbox_events (
                        event_id VARCHAR(100) PRIMARY KEY,
                        aggregate_id VARCHAR(100) NOT NULL,
                        payload TEXT NOT NULL,
                        sent_at TIMESTAMPTZ
                    )
                    """);
        }
    }

    @BeforeEach
    void clearDatabase() throws SQLException {
        try (Connection connection = databaseConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE processed_events, payments, outbox_events");
        }
    }

    @Test
    void keepsRecordsWithTheSameKeyInOnePartitionAndInOffsetOrder() throws Exception {
        String topic = createTopic("ordering", 3);
        String orderId = "order-42";

        List<RecordMetadata> metadata = new ArrayList<>();
        try (KafkaProducer<String, String> producer = producer()) {
            for (int sequence = 1; sequence <= 5; sequence++) {
                metadata.add(producer.send(new ProducerRecord<>(
                        topic, orderId, Integer.toString(sequence))).get());
            }
        }

        try (KafkaConsumer<String, String> consumer = consumer(uniqueGroup())) {
            consumer.subscribe(List.of(topic));
            List<ConsumerRecord<String, String>> records = pollExactly(consumer, 5);

            assertThat(metadata).extracting(RecordMetadata::partition).containsOnly(metadata.getFirst().partition());
            assertThat(records).extracting(ConsumerRecord::partition).containsOnly(metadata.getFirst().partition());
            assertThat(records).extracting(ConsumerRecord::value)
                    .containsExactly("1", "2", "3", "4", "5");
            assertThat(records).extracting(ConsumerRecord::offset).isSorted();
        }
    }

    @Test
    void redeliversRecordWhenConsumerClosesWithoutCommittingItsOffset() throws Exception {
        String topic = createTopic("redelivery", 1);
        String group = uniqueGroup();
        publish(topic, "order-1", "event-1");

        ConsumerRecord<String, String> firstDelivery;
        try (KafkaConsumer<String, String> firstConsumer = consumer(group)) {
            firstConsumer.subscribe(List.of(topic));
            firstDelivery = pollOne(firstConsumer);
            assertThat(committedOffset(firstConsumer, partition(firstDelivery))).isNull();
            // Closing without commit simulates a crash after processing started.
        }

        try (KafkaConsumer<String, String> restartedConsumer = consumer(group)) {
            restartedConsumer.subscribe(List.of(topic));
            ConsumerRecord<String, String> redelivery = pollOne(restartedConsumer);

            assertThat(redelivery.partition()).isEqualTo(firstDelivery.partition());
            assertThat(redelivery.offset()).isEqualTo(firstDelivery.offset());
            assertThat(redelivery.value()).isEqualTo(firstDelivery.value());
        }
    }

    @Test
    void commitsKafkaOffsetOnlyAfterDatabaseTransactionCommits() throws Exception {
        String topic = createTopic("transaction-boundary", 1);
        String group = uniqueGroup();
        publish(topic, "order-2", "event-2");

        ConsumerRecord<String, String> failedAttempt;
        try (KafkaConsumer<String, String> consumer = consumer(group)) {
            consumer.subscribe(List.of(topic));
            failedAttempt = pollOne(consumer);

            assertThrows(SimulatedCrash.class, () -> insertPaymentAndCrash(failedAttempt.key()));
            assertThat(rowCount("payments")).isZero();
            assertThat(committedOffset(consumer, partition(failedAttempt))).isNull();
        }

        try (KafkaConsumer<String, String> restartedConsumer = consumer(group)) {
            restartedConsumer.subscribe(List.of(topic));
            ConsumerRecord<String, String> redelivery = pollOne(restartedConsumer);
            assertThat(redelivery.offset()).isEqualTo(failedAttempt.offset());

            insertPayment(redelivery.key());
            commitRecord(restartedConsumer, redelivery);

            assertThat(rowCount("payments")).isOne();
            assertThat(committedOffset(restartedConsumer, partition(redelivery)).offset())
                    .isEqualTo(redelivery.offset() + 1);
        }
    }

    @Test
    void appliesBusinessEffectOnceWhenPostgresUniqueMarkerSeesDuplicateEvents() throws Exception {
        String topic = createTopic("idempotency", 1);
        String eventId = "event-duplicate";
        String orderId = "order-3";
        publish(topic, orderId, eventId);
        publish(topic, orderId, eventId);

        try (KafkaConsumer<String, String> consumer = consumer(uniqueGroup())) {
            consumer.subscribe(List.of(topic));
            for (ConsumerRecord<String, String> record : pollExactly(consumer, 2)) {
                processIdempotently(record.value(), record.key());
                commitRecord(consumer, record);
            }
        }

        assertThat(rowCount("processed_events")).isOne();
        assertThat(paymentHandledEvents(orderId)).isOne();
    }

    @Test
    void movesPoisonRecordThroughRetryTopicToDlqWithDiagnosticHeaders() throws Exception {
        String sourceTopic = createTopic("payments", 1);
        String retryTopic = createTopic("payments-retry", 1);
        String dlqTopic = createTopic("payments-dlq", 1);
        publish(sourceTopic, "order-4", "poison-event");

        ConsumerRecord<String, String> sourceRecord;
        try (KafkaConsumer<String, String> sourceConsumer = consumer(uniqueGroup())) {
            sourceConsumer.subscribe(List.of(sourceTopic));
            sourceRecord = pollOne(sourceConsumer);
            publishFailure(retryTopic, sourceRecord, 1,
                    new IllegalStateException("payment provider unavailable"));
            commitRecord(sourceConsumer, sourceRecord);
        }

        try (KafkaConsumer<String, String> retryConsumer = consumer(uniqueGroup())) {
            retryConsumer.subscribe(List.of(retryTopic));
            ConsumerRecord<String, String> retryRecord = pollOne(retryConsumer);
            publishFailure(dlqTopic, retryRecord, 2,
                    new IllegalArgumentException("invalid payment event"));
            commitRecord(retryConsumer, retryRecord);
        }

        try (KafkaConsumer<String, String> dlqConsumer = consumer(uniqueGroup())) {
            dlqConsumer.subscribe(List.of(dlqTopic));
            ConsumerRecord<String, String> deadLetter = pollOne(dlqConsumer);

            assertThat(deadLetter.key()).isEqualTo(sourceRecord.key());
            assertThat(deadLetter.value()).isEqualTo(sourceRecord.value());
            assertThat(header(deadLetter, ORIGINAL_TOPIC)).isEqualTo(sourceTopic);
            assertThat(header(deadLetter, ORIGINAL_PARTITION))
                    .isEqualTo(Integer.toString(sourceRecord.partition()));
            assertThat(header(deadLetter, ORIGINAL_OFFSET))
                    .isEqualTo(Long.toString(sourceRecord.offset()));
            assertThat(header(deadLetter, RETRY_COUNT)).isEqualTo("2");
            assertThat(header(deadLetter, ERROR_CLASS)).isEqualTo(IllegalArgumentException.class.getName());
            assertThat(header(deadLetter, ERROR_MESSAGE)).isEqualTo("invalid payment event");
        }
    }

    @Test
    void republishesOutboxRecordAfterCrashBetweenKafkaAckAndDatabaseMark() throws Exception {
        String topic = createTopic("outbox", 1);
        String eventId = "outbox-event-1";
        insertOutboxEvent(eventId, "order-5", "order-created");

        try (KafkaProducer<String, String> producer = producer()) {
            assertThrows(SimulatedCrash.class,
                    () -> publishOutboxBatch(producer, topic, true));
            assertThat(outboxSent(eventId)).isFalse();

            publishOutboxBatch(producer, topic, false);
            assertThat(outboxSent(eventId)).isTrue();
        }

        try (KafkaConsumer<String, String> consumer = consumer(uniqueGroup())) {
            consumer.subscribe(List.of(topic));
            List<ConsumerRecord<String, String>> deliveries = pollExactly(consumer, 2);

            assertThat(deliveries).extracting(ConsumerRecord::key)
                    .containsExactly(eventId, eventId);
            assertThat(deliveries).extracting(ConsumerRecord::value)
                    .containsExactly("order-created", "order-created");
        }
    }

    private static String createTopic(String purpose, int partitions) throws Exception {
        String topic = purpose + "-" + UUID.randomUUID();
        try (AdminClient admin = AdminClient.create(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(topic, partitions, (short) 1))).all().get();
        }
        return topic;
    }

    private static KafkaProducer<String, String> producer() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new KafkaProducer<>(properties);
    }

    private static KafkaConsumer<String, String> consumer(String groupId) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(properties);
    }

    private static void publish(String topic, String key, String value) throws Exception {
        try (KafkaProducer<String, String> producer = producer()) {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        }
    }

    private static ConsumerRecord<String, String> pollOne(KafkaConsumer<String, String> consumer) {
        return pollExactly(consumer, 1).getFirst();
    }

    private static List<ConsumerRecord<String, String>> pollExactly(
            KafkaConsumer<String, String> consumer,
            int expectedRecords
    ) {
        long deadline = System.nanoTime() + POLL_TIMEOUT.toNanos();
        List<ConsumerRecord<String, String>> result = new ArrayList<>();
        while (result.size() < expectedRecords && System.nanoTime() < deadline) {
            ConsumerRecords<String, String> batch = consumer.poll(Duration.ofMillis(250));
            batch.forEach(result::add);
        }
        assertThat(result)
                .as("Kafka records received before timeout")
                .hasSize(expectedRecords);
        return result;
    }

    private static void commitRecord(
            KafkaConsumer<String, String> consumer,
            ConsumerRecord<String, String> record
    ) {
        consumer.commitSync(Map.of(
                partition(record),
                new OffsetAndMetadata(record.offset() + 1)));
    }

    private static TopicPartition partition(ConsumerRecord<String, String> record) {
        return new TopicPartition(record.topic(), record.partition());
    }

    private static OffsetAndMetadata committedOffset(
            KafkaConsumer<String, String> consumer,
            TopicPartition partition
    ) {
        return consumer.committed(Set.of(partition)).get(partition);
    }

    private static String uniqueGroup() {
        return "stage-2b-" + UUID.randomUUID();
    }

    private static Connection databaseConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void insertPaymentAndCrash(String orderId) throws SQLException {
        try (Connection connection = databaseConnection()) {
            connection.setAutoCommit(false);
            insertPayment(connection, orderId);
            connection.rollback();
            throw new SimulatedCrash("consumer crashed before database commit");
        }
    }

    private static void insertPayment(String orderId) throws SQLException {
        try (Connection connection = databaseConnection()) {
            insertPayment(connection, orderId);
        }
    }

    private static void insertPayment(Connection connection, String orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO payments(order_id, handled_events) VALUES (?, 1)")) {
            statement.setString(1, orderId);
            statement.executeUpdate();
        }
    }

    private static void processIdempotently(String eventId, String orderId) throws SQLException {
        try (Connection connection = databaseConnection()) {
            connection.setAutoCommit(false);
            try {
                int markerInserted;
                try (PreparedStatement marker = connection.prepareStatement("""
                        INSERT INTO processed_events(event_id) VALUES (?)
                        ON CONFLICT DO NOTHING
                        """)) {
                    marker.setString(1, eventId);
                    markerInserted = marker.executeUpdate();
                }

                if (markerInserted == 1) {
                    try (PreparedStatement businessEffect = connection.prepareStatement("""
                            INSERT INTO payments(order_id, handled_events) VALUES (?, 1)
                            ON CONFLICT (order_id) DO UPDATE
                            SET handled_events = payments.handled_events + 1
                            """)) {
                        businessEffect.setString(1, orderId);
                        businessEffect.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void publishFailure(
            String destinationTopic,
            ConsumerRecord<String, String> source,
            int retryCount,
            RuntimeException failure
    ) throws ExecutionException, InterruptedException {
        Map<String, String> headers = new HashMap<>();
        headers.put(ORIGINAL_TOPIC, existingHeaderOr(source, ORIGINAL_TOPIC, source.topic()));
        headers.put(ORIGINAL_PARTITION, existingHeaderOr(
                source, ORIGINAL_PARTITION, Integer.toString(source.partition())));
        headers.put(ORIGINAL_OFFSET, existingHeaderOr(
                source, ORIGINAL_OFFSET, Long.toString(source.offset())));
        headers.put(RETRY_COUNT, Integer.toString(retryCount));
        headers.put(ERROR_CLASS, failure.getClass().getName());
        headers.put(ERROR_MESSAGE, failure.getMessage());

        ProducerRecord<String, String> failedRecord =
                new ProducerRecord<>(destinationTopic, source.key(), source.value());
        headers.forEach((name, value) -> failedRecord.headers().add(
                new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8))));
        try (KafkaProducer<String, String> producer = producer()) {
            producer.send(failedRecord).get();
        }
    }

    private static String existingHeaderOr(
            ConsumerRecord<String, String> record,
            String name,
            String fallback
    ) {
        Header header = record.headers().lastHeader(name);
        return header == null ? fallback : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        assertThat(header).as("Kafka header %s", name).isNotNull();
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private static void insertOutboxEvent(String eventId, String aggregateId, String payload)
            throws SQLException {
        try (Connection connection = databaseConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO outbox_events(event_id, aggregate_id, payload)
                     VALUES (?, ?, ?)
                     """)) {
            statement.setString(1, eventId);
            statement.setString(2, aggregateId);
            statement.setString(3, payload);
            statement.executeUpdate();
        }
    }

    private static void publishOutboxBatch(
            KafkaProducer<String, String> producer,
            String topic,
            boolean crashAfterKafkaAck
    ) throws SQLException, ExecutionException, InterruptedException {
        try (Connection connection = databaseConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement("""
                    SELECT event_id, payload
                    FROM outbox_events
                    WHERE sent_at IS NULL
                    FOR UPDATE
                    """)) {
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        String eventId = rows.getString("event_id");
                        String payload = rows.getString("payload");
                        producer.send(new ProducerRecord<>(topic, eventId, payload)).get();

                        if (crashAfterKafkaAck) {
                            connection.rollback();
                            throw new SimulatedCrash(
                                    "publisher crashed after Kafka ack but before sent_at update");
                        }

                        try (PreparedStatement markSent = connection.prepareStatement("""
                                UPDATE outbox_events SET sent_at = now() WHERE event_id = ?
                                """)) {
                            markSent.setString(1, eventId);
                            markSent.executeUpdate();
                        }
                    }
                }
            }
            connection.commit();
        }
    }

    private static boolean outboxSent(String eventId) throws SQLException {
        try (Connection connection = databaseConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT sent_at IS NOT NULL FROM outbox_events WHERE event_id = ?")) {
            statement.setString(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }

    private static long rowCount(String table) throws SQLException {
        if (!List.of("payments", "processed_events", "outbox_events").contains(table)) {
            throw new IllegalArgumentException("Unexpected table: " + table);
        }
        try (Connection connection = databaseConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT count(*) FROM " + table)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static int paymentHandledEvents(String orderId) throws SQLException {
        try (Connection connection = databaseConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT handled_events FROM payments WHERE order_id = ?")) {
            statement.setString(1, orderId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        }
    }

    private static final class SimulatedCrash extends RuntimeException {

        private SimulatedCrash(String message) {
            super(message);
        }
    }
}
