package pl.jakubtworek.marketplace.integrationtest;

import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singletonowy kontener Kafki dla testów integracyjnych.
 *
 * Nie używamy @Container ani @Testcontainers, żeby uniknąć problemów z cyklem życia
 * kontenera i cache'owaniem Spring ApplicationContext.
 */
public final class IntegrationKafkaContainer {

    private static final ConfluentKafkaContainer INSTANCE =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    static {
        INSTANCE.start();
    }

    private IntegrationKafkaContainer() {
    }

    public static ConfluentKafkaContainer getInstance() {
        return INSTANCE;
    }
}
