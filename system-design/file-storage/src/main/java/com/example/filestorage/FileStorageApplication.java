package com.example.filestorage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableScheduling
@EnableAsync
@SpringBootApplication
@ConfigurationPropertiesScan
public class FileStorageApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileStorageApplication.class, args);
    }
}
