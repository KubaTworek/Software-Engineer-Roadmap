package com.example.videostreaming.storage;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectStorageServiceTest {
    @Test
    void signsUrlForPublicOriginBecauseHostIsPartOfSignature() throws Exception {
        StorageProperties properties = new StorageProperties(
                "http://minio:9000", "https://media.example.test:9443",
                "access-key", "secret-key", "video", "us-east-1", 15,
                "https://cdn.example.test");
        MinioClient internal = MinioClient.builder().endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey()).region(properties.region()).build();
        MinioClient external = MinioClient.builder().endpoint(properties.publicEndpoint())
                .credentials(properties.accessKey(), properties.secretKey()).region(properties.region()).build();
        ObjectStorageService storage = new ObjectStorageService(internal, external, properties);

        URI result = URI.create(storage.presignedPutUrl("raw/video.mp4"));

        assertThat(result.getHost()).isEqualTo("media.example.test");
        assertThat(result.getPort()).isEqualTo(9443);
        assertThat(result.getRawQuery()).contains("X-Amz-Signature");
    }
}
