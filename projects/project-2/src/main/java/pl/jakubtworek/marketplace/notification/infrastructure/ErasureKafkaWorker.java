package pl.jakubtworek.marketplace.notification.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import pl.jakubtworek.marketplace.notification.application.ErasureOutboxRelay;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component @Profile({"postgres"})
@ConditionalOnProperty(name="marketplace.privacy.worker-enabled",havingValue="true")
public final class ErasureKafkaWorker implements AutoCloseable {
    private final KafkaProducer<String,String> producer;
    private final ErasureOutboxRelay relay;
    private final String topic;
    public ErasureKafkaWorker(JdbcTemplate jdbc,PlatformTransactionManager tx,
            @Value("${spring.kafka.bootstrap-servers}") String servers,
            @Value("${marketplace.privacy.topic:marketplace.privacy-events.v1}") String topic) {
        this.topic=topic;relay=new ErasureOutboxRelay(jdbc,tx);
        producer=new KafkaProducer<>(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,servers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class,ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,StringSerializer.class,
                ProducerConfig.ACKS_CONFIG,"all",ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,true,ProducerConfig.MAX_BLOCK_MS_CONFIG,2000,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,1000,ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,2000));
    }
    @Scheduled(fixedDelayString="${marketplace.privacy.poll-delay:2s}")
    public int publish() {return relay.publish((event,subject)->{
        try {producer.send(new ProducerRecord<>(topic,subject.toString(),"{\"eventId\":\""+event+"\",\"subjectReference\":\""+subject+"\",\"type\":\"ContactErased\"}")).get(3,TimeUnit.SECONDS);}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("Erasure publish interrupted");}
        catch(Exception unavailable){throw new IllegalStateException("Erasure broker unavailable");}
    });}
    public void close(){producer.close(java.time.Duration.ofSeconds(2));}
}
