package com.example.filestorage.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class MinioConfig {
    @Bean
    MinioClient minioClient(StorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @Bean
    CommandLineRunner createBucketIfMissing(MinioClient minioClient, StorageProperties properties) {
        return args -> {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
            }
        };
    }
}
