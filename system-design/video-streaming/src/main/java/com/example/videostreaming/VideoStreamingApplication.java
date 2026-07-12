package com.example.videostreaming;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableScheduling
@EnableCaching
@EnableAsync
@SpringBootApplication
public class VideoStreamingApplication {
    public static void main(String[] args) {
        SpringApplication.run(VideoStreamingApplication.class, args);
    }
}
