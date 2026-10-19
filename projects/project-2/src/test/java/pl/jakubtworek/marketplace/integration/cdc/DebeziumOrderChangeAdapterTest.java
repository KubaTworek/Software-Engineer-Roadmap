package pl.jakubtworek.marketplace.integration.cdc;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebeziumOrderChangeAdapterTest {
    private final DebeziumOrderChangeAdapter adapter = new DebeziumOrderChangeAdapter(new JsonMapper());

    @Test void mapsCreateAndDeleteWithoutConfusingSourceLsnWithKafkaOffset() {
        UUID id = UUID.randomUUID();
        var created = adapter.fromKafka(2, 41, """
                {"payload":{"op":"c","after":{"id":"%s","status":"PENDING"},"source":{"lsn":991}}}
                """.formatted(id));
        var deleted = adapter.fromKafka(2, 42, """
                {"payload":{"op":"d","before":{"id":"%s","status":"PENDING"},"after":null}}
                """.formatted(id));

        assertThat(created.position()).isEqualTo(new CdcProjectionPipeline.Position(2, 41));
        assertThat(created.value().status()).isEqualTo("PENDING");
        assertThat(deleted.deleted()).isTrue();
    }

    @Test void rejectsSchemaDriftInsteadOfAdvancingWithInventedData() {
        assertThatThrownBy(() -> adapter.fromKafka(0, 1, "{\"payload\":{\"op\":\"u\",\"after\":{}}}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("id");
    }
}
