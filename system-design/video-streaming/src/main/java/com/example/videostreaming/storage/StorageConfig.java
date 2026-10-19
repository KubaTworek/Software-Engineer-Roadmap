package com.example.videostreaming.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class StorageConfig {
    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    @Primary
    MinioClient minioClient(StorageProperties props) {
        /*
         * Zbudowanie klienta nie wykonuje I/O. Dzięki temu sam start kontekstu,
         * test jednostkowy i narzędzia analizujące beany nie zależą od dostępności
         * MinIO. Sprawdzenie bucketa jest osobnym krokiem lifecycle poniżej.
         */
        return MinioClient.builder()
                .endpoint(props.endpoint())
                .credentials(props.accessKey(), props.secretKey())
                .region(props.region())
                .build();
    }

    /**
     * Podpis SigV4 obejmuje nagłówek Host, dlatego nie wolno podpisać adresu
     * `minio:9000`, a później tylko zamienić go na `localhost:9000`. Osobny
     * klient podpisuje od początku origin widoczny dla przeglądarki; nie wykonuje
     * przy tym połączenia z publicznym endpointem.
     */
    @Bean("publicMinioClient")
    MinioClient publicMinioClient(StorageProperties props) {
        return MinioClient.builder()
                .endpoint(props.publicEndpoint())
                .credentials(props.accessKey(), props.secretKey())
                .region(props.region())
                .build();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.storage",
            name = "initialize-bucket",
            havingValue = "true",
            matchIfMissing = true
    )
    ApplicationRunner bucketInitializer(MinioClient client, StorageProperties props) {
        return arguments -> {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(props.bucket()).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(props.bucket()).build());
                log.info("Created MinIO bucket {}", props.bucket());
            }
        };
    }
}
