package pl.jakubtworek.marketplace.integrationtest;

import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singletonowy kontener Kafki dla testów integracyjnych.
 *
 * Nie używamy @Container ani @Testcontainers, żeby uniknąć problemów z cyklem życia
 * kontenera i cache'owaniem Spring ApplicationContext.
 */
public final class IntegrationKafkaContainer {

    private static final KafkaContainer INSTANCE =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    static {
        INSTANCE.start();
    }

    private IntegrationKafkaContainer() {
    }

    public static KafkaContainer getInstance() {
        return INSTANCE;
    }
}