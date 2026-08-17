package com.example.videostreaming.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {
    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    MinioClient minioClient(StorageProperties props) throws Exception {
        MinioClient client = MinioClient.builder()
                .endpoint(props.endpoint())
                .credentials(props.accessKey(), props.secretKey())
                .build();
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(props.bucket()).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(props.bucket()).build());
            log.info("Created MinIO bucket {}", props.bucket());
        }
        return client;
    }
}
