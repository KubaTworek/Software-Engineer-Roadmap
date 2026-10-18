package com.example.videostreaming;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:video_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.cache.type=simple",
        "app.storage.initialize-bucket=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false",
        "app.search.enabled=false",
        "app.transcoding.enabled=false",
        "app.transcoding.worker-enabled=false",
        "app.live.enabled=false",
        "app.live.worker-enabled=false",
        "app.personalization.enabled=false",
        "app.qoe.enabled=false",
        "app.cdn.prewarm-enabled=false"
})
class VideoStreamingApplicationTests {
    @Test
    void contextLoads() {}
}
