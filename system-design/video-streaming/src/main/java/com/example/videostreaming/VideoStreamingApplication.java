package com.example.videostreaming;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class VideoStreamingApplication {
    public static void main(String[] args) {
        SpringApplication.run(VideoStreamingApplication.class, args);
    }
}
