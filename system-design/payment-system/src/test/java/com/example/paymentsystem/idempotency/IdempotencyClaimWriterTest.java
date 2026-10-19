package com.example.paymentsystem.idempotency;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "spring.flyway.enabled=false"
})
class IdempotencyClaimWriterTest {
    @Autowired IdempotencyClaimWriter writer;
    @Autowired IdempotencyRecordRepository repository;

    @Test
    void staleContenderCannotMergeOverCommittedWinner() {
        String key = "insert-only-" + UUID.randomUUID();
        Instant lease = Instant.now().plusSeconds(120);
        // Both contenders have already constructed their records after an absent
        // lookup. Commit the winner first: this reproduces the dangerous merge
        // interleaving deterministically, without depending on thread scheduling.
        var winner = new IdempotencyRecord(key, "test", "winner", lease);
        var contender = new IdempotencyRecord(key, "test", "contender", lease);
        writer.insert(winner);

        assertThatThrownBy(() -> writer.insert(contender))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.findById(new IdempotencyRecordId(key, "test")).orElseThrow()
                .getRequestHash()).isEqualTo("winner");
    }
}
