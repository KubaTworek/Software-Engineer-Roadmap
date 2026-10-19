package com.example.observability.server.cold;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectStorageServiceTest {
    @Test
    void publishesCompleteObjectAndRejectsTraversal(@TempDir Path root) {
        var storage = new ObjectStorageService(root.toString());
        assertThat(storage.putGzipLines("logs/tenant-a/hour.ndjson.gz", List.of("{\"ok\":true}")))
                .isEqualTo("object://logs/tenant-a/hour.ndjson.gz");
        assertThat(storage.list("logs/tenant-a")).hasSize(2)
                .anyMatch(info -> info.uri().endsWith(".manifest.json"));
        assertThatThrownBy(() -> storage.putGzipLines("../escape.gz", List.of("secret")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restoreDrillVerifiesManifestAndLoadsOnlyIntoIsolatedTable(@TempDir Path root) {
        var storage = new ObjectStorageService(root.toString());
        String key = "logs/tenant-a/hour.ndjson.gz";
        storage.putGzipLines(key, List.of("{\"event\":1}", "{\"event\":2}"));
        var inserted = new AtomicInteger();
        JdbcTemplate recordingJdbc = new JdbcTemplate() {
            @Override public int update(String sql, Object... args) {
                assertThat(sql).contains("restore_drill_rows");
                assertThat(args[1]).isEqualTo("tenant-a");
                inserted.incrementAndGet();
                return 1;
            }
        };

        var result = new ColdRestoreService(storage, new ObjectMapper(), recordingJdbc)
                .drill("tenant-a", key);

        assertThat(result.status()).isEqualTo("verified");
        assertThat(result.rows()).isEqualTo(2);
        assertThat(inserted).hasValue(2);
        assertThatThrownBy(() -> new ColdRestoreService(storage, new ObjectMapper(), recordingJdbc)
                .drill("tenant-b", key)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restoreDrillRejectsTamperedObjectBeforeLoadingRows(@TempDir Path root) throws Exception {
        var storage = new ObjectStorageService(root.toString());
        String key = "metrics/tenant-a/hour.ndjson.gz";
        storage.putGzipLines(key, List.of("{\"value\":7}"));
        Files.write(root.resolve(key), new byte[]{1, 2, 3});

        assertThatThrownBy(() -> new ColdRestoreService(storage, new ObjectMapper(), new JdbcTemplate())
                .drill("tenant-a", key))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checksum");
    }
}
