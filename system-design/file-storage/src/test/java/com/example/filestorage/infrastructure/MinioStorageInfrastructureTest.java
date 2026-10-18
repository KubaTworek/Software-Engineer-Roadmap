package com.example.filestorage.infrastructure;

import com.example.filestorage.storage.StorageProperties;
import com.example.filestorage.storage.StorageService;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.images.builder.ImageFromDockerfile;
import java.nio.file.Path;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Kontrakt z prawdziwym S3-compatible storage: compose zachowuje kolejnosc chunkow. */
@Testcontainers(disabledWithoutDocker = true)
class MinioStorageInfrastructureTest {
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin123";
    private static final String BUCKET = "file-storage-test";

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            new ImageFromDockerfile().withFileFromPath("Dockerfile", Path.of("docker/minio.Dockerfile")))
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data")
            .withExposedPorts(9000);

    @Test
    void composesDownloadsAndDeletesObject() throws Exception {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        MinioClient client = MinioClient.builder().endpoint(endpoint)
                .credentials(ACCESS_KEY, SECRET_KEY).build();
        client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
        StorageService storage = new StorageService(client,
                new StorageProperties(endpoint, ACCESS_KEY, SECRET_KEY, BUCKET, endpoint));

        byte[] first = new byte[5 * 1024 * 1024];
        java.util.Arrays.fill(first, (byte) 'a');
        byte[] second = "END".getBytes(StandardCharsets.UTF_8);
        storage.put("tmp/0", new ByteArrayInputStream(first), first.length, "application/octet-stream");
        storage.put("tmp/1", new ByteArrayInputStream(second), second.length, "application/octet-stream");

        storage.compose("final/blob", List.of("tmp/0", "tmp/1"), "application/octet-stream");

        assertThat(storage.size("final/blob")).isEqualTo(first.length + second.length);
        assertThat(storage.download("final/blob").readAllBytes())
                .startsWith((byte) 'a').endsWith(second);
        assertThat(storage.listObjectKeys("final/", 10)).containsExactly("final/blob");
        storage.delete("final/blob");
        assertThat(storage.exists("final/blob")).isFalse();
    }
}
