package com.example.videostreaming.infrastructure;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RabbitDeadLetterInfrastructureTest {
    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    @Test
    void rejectedPersistentJobIsRoutedToDlq() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(RABBIT.getAmqpUrl());
        try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
            channel.exchangeDeclare("video.test", "direct", true);
            channel.queueDeclare("video.test.dlq", true, false, false, null);
            channel.queueBind("video.test.dlq", "video.test", "dead");
            channel.queueDeclare("video.test.jobs", true, false, false,
                    Map.of("x-dead-letter-exchange", "video.test", "x-dead-letter-routing-key", "dead"));
            channel.queueBind("video.test.jobs", "video.test", "job");
            channel.basicPublish("video.test", "job",
                    new AMQP.BasicProperties.Builder().deliveryMode(2).build(), "job-1".getBytes());

            var delivery = channel.basicGet("video.test.jobs", false);
            channel.basicReject(delivery.getEnvelope().getDeliveryTag(), false);

            com.rabbitmq.client.GetResponse dead = null;
            for (int i = 0; i < 20 && dead == null; i++) {
                Thread.sleep(50);
                dead = channel.basicGet("video.test.dlq", true);
            }
            assertThat(dead).isNotNull();
            assertThat(new String(dead.getBody())).isEqualTo("job-1");
        }
    }

    @Test
    void retryQueueDelaysMessageAndReturnsItToMainQueue() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(RABBIT.getAmqpUrl());
        try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
            channel.exchangeDeclare("video.retry", "direct", true);
            channel.queueDeclare("video.retry.jobs", true, false, false, null);
            channel.queueBind("video.retry.jobs", "video.retry", "job");
            channel.queueDeclare("video.retry.delay", true, false, false,
                    Map.of("x-message-ttl", 200,
                            "x-dead-letter-exchange", "video.retry",
                            "x-dead-letter-routing-key", "job"));
            channel.queueBind("video.retry.delay", "video.retry", "retry");

            channel.basicPublish("video.retry", "retry",
                    new AMQP.BasicProperties.Builder().deliveryMode(2).build(), "job-2".getBytes());
            assertThat(channel.basicGet("video.retry.jobs", true)).isNull();

            com.rabbitmq.client.GetResponse retried = null;
            for (int i = 0; i < 30 && retried == null; i++) {
                Thread.sleep(50);
                retried = channel.basicGet("video.retry.jobs", true);
            }
            assertThat(retried).isNotNull();
            assertThat(new String(retried.getBody())).isEqualTo("job-2");
        }
    }
}
