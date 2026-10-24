package pl.jakubtworek.marketplace.integrationtest;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import pl.jakubtworek.marketplace.notification.application.*;
import pl.jakubtworek.marketplace.notification.infrastructure.ErasureKafkaWorker;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContactErasureRestoreIntegrationTest extends AbstractIntegrationTest {
    @Autowired ContactLifecycle contacts;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Environment environment;
    @Test void outerRollbackDoesNotExposeUncommittedContactAndCommitInvalidatesCachedVersion() {
        UUID id=UUID.randomUUID();
        var original=new ContactLifecycle.Contact(id,"original@example.test","Original");
        var updated=new ContactLifecycle.Contact(id,"updated@example.test","Updated");
        contacts.put(original);assertEquals(original,contacts.read(id).orElseThrow());
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status->{
            contacts.put(updated);assertEquals(updated,contacts.read(id).orElseThrow());status.setRollbackOnly();
        });
        assertEquals(original,contacts.read(id).orElseThrow());
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status->contacts.put(updated));
        assertEquals(updated,contacts.read(id).orElseThrow());
    }
    @Test @Timeout(90) void eraseSurvivesCacheRetryKafkaAndRestoreOfOlderDatabase(@TempDir Path directory) throws Exception {
        UUID subject=UUID.randomUUID(),request=UUID.randomUUID();
        var contact=new ContactLifecycle.Contact(subject,"erase-me@example.test","Private Customer");
        contacts.put(contact);assertEquals(contact,contacts.read(subject).orElseThrow()); // Zapełnia cache.
        var pg=IntegrationPostgresContainer.getInstance();
        String suffix=UUID.randomUUID().toString().replace("-","");
        String archive="/tmp/privacy-"+suffix+".dump",target="privacy_restore_"+suffix;
        assertEquals(0,pg.execInContainer("pg_dump","-U",pg.getUsername(),"-d",pg.getDatabaseName(),"-Fc","-f",archive).getExitCode());
        assertEquals(request,contacts.erase(subject,request));
        assertEquals(request,contacts.erase(subject,UUID.randomUUID()));
        assertTrue(contacts.read(subject).isEmpty());
        assertEquals(0,jdbcTemplate.queryForObject("select count(*) from notification.contact_projection where customer_id=?",Integer.class,subject));
        assertThrows(IllegalStateException.class,()->contacts.put(contact)); // Spóźnione zdarzenie/import nie odtwarza PII.
        contacts.erase(UUID.randomUUID(),UUID.randomUUID()); // Umożliwia sprawdzenie utraty środka registry przy zachowanym watermark.
        var relay=new ErasureOutboxRelay(jdbcTemplate,transactions);
        assertThrows(IllegalStateException.class,()->relay.publish((id,ref)->{throw new IllegalStateException("broker down");}));
        assertEquals(1,jdbcTemplate.queryForObject("select count(*) from notification.erasure_outbox where event_id=? and published_at is null",Integer.class,request));
        String bootstrap=environment.getRequiredProperty("spring.kafka.bootstrap-servers"),topic="privacy-"+suffix;
        try(var admin=AdminClient.create(Map.of("bootstrap.servers",bootstrap))){admin.createTopics(List.of(new NewTopic(topic,1,(short)1))).all().get();}
        try(var worker=new ErasureKafkaWorker(jdbcTemplate,transactions,bootstrap,topic)) { assertTrue(worker.publish()>=1); }
        try(var consumer=new KafkaConsumer<String,String>(Map.of("bootstrap.servers",bootstrap,"group.id","privacy-test-"+suffix,
                "key.deserializer",StringDeserializer.class,"value.deserializer",StringDeserializer.class,"auto.offset.reset","earliest","enable.auto.commit",false))){
            consumer.subscribe(List.of(topic));String payload=null;
            long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
            while(payload==null&&System.nanoTime()<deadline)for(var record:consumer.poll(Duration.ofMillis(200)))if(record.key().equals(subject.toString()))payload=record.value();
            assertNotNull(payload);assertTrue(payload.contains(request.toString()));assertFalse(payload.contains(contact.email()));assertFalse(payload.contains(contact.displayName()));
        }
        // Niezależny eksport PO erase jest nowszy niż dump sprzed erase. W DR musi być poza utraconą bazą.
        var json=new tools.jackson.databind.ObjectMapper();
        var registry=contacts.exportRegistry();var seal=ContactLifecycle.seal(registry);
        Path body=directory.resolve("registry.json"),manifest=directory.resolve("trusted-manifest.json");
        json.writeValue(body.toFile(),registry);json.writeValue(manifest.toFile(),seal);
        assertEquals(0,pg.execInContainer("createdb","-U",pg.getUsername(),target).getExitCode());
        try {
            assertEquals(0,pg.execInContainer("pg_restore","--exit-on-error","--no-owner","-U",pg.getUsername(),"-d",target,archive).getExitCode());
            var data=new DriverManagerDataSource("jdbc:postgresql://"+pg.getHost()+":"+pg.getMappedPort(5432)+"/"+target,pg.getUsername(),pg.getPassword());
            var restored=new ContactLifecycle(new JdbcTemplate(data),new DataSourceTransactionManager(data));
            assertEquals(contact,restored.read(subject).orElseThrow()); // Tylko maintenance: ruch nadal zamknięty.
            var external=json.readValue(body.toFile(),ContactLifecycle.Registry.class);
            var trusted=json.readValue(manifest.toFile(),ContactLifecycle.RegistrySeal.class);
            assertThrows(IllegalArgumentException.class,()->restored.suppressAfterRestore(new ContactLifecycle.Registry(0,List.of()),trusted));
            var truncated=new ContactLifecycle.Registry(external.watermark(),List.of(external.entries().getLast()));
            assertThrows(IllegalArgumentException.class,()->restored.suppressAfterRestore(truncated,trusted));
            restored.suppressAfterRestore(external,trusted);
            assertTrue(restored.read(subject).isEmpty());
            assertThrows(IllegalStateException.class,()->restored.put(contact));
            assertEquals(0,new JdbcTemplate(data).queryForObject("select count(*) from notification.contact_projection where customer_id=?",Integer.class,subject));
            restored.suppressAfterRestore(external,trusted); // Retry maintenance pozostaje idempotentny.
        } finally {
            pg.execInContainer("dropdb","-U",pg.getUsername(),target);
            pg.execInContainer("rm","-f",archive);
        }
    }
}
