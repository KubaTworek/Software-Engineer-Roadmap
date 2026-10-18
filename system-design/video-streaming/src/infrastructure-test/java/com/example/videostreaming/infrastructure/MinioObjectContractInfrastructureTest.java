package com.example.videostreaming.infrastructure;

import io.minio.*;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MinioObjectContractInfrastructureTest {
    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z"))
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server", "/data")
            .withExposedPorts(9000);

    @Test
    void uploadedSegmentCanBeVerifiedBeforeManifestPublication() throws Exception {
        MinioClient client = MinioClient.builder()
                .endpoint("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000))
                .credentials("minioadmin", "minioadmin")
                .build();
        client.makeBucket(MakeBucketArgs.builder().bucket("video-test").build());
        byte[] bytes = "segment".getBytes(StandardCharsets.UTF_8);
        client.putObject(PutObjectArgs.builder().bucket("video-test").object("video/360p/segment.ts")
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                .contentType("video/mp2t").build());

        StatObjectResponse metadata = client.statObject(StatObjectArgs.builder()
                .bucket("video-test").object("video/360p/segment.ts").build());
        assertThat(metadata.size()).isEqualTo(bytes.length);
        assertThat(metadata.contentType()).isEqualTo("video/mp2t");
    }
}
