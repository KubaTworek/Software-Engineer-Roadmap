package pl.jakubtworek.chatsystem.media;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class MinioObjectStorageIT {
    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z"))
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server", "/data")
            .withExposedPorts(9000);

    @Test
    void quarantineIsSeparateFromCleanAndPromotionMovesTheObject() throws Exception {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        MinioObjectStorage storage = new MinioObjectStorage(endpoint, "minioadmin", "minioadmin",
                "chat-quarantine", "chat-clean");
        String key = "user/file.txt";
        byte[] content = "safe".getBytes(StandardCharsets.UTF_8);

        storage.putQuarantine(key, content, "text/plain");
        assertThat(storage.readQuarantine(key)).isEqualTo(content);
        assertThatThrownBy(() -> storage.readClean(key)).isInstanceOf(java.io.IOException.class);
        storage.promoteToClean(key);
        assertThat(storage.readClean(key)).isEqualTo(content);
        assertThatThrownBy(() -> storage.readQuarantine(key)).isInstanceOf(java.io.IOException.class);
    }
}
