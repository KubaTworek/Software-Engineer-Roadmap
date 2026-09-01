package com.example.demoapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.example.demoapi.config.AppProperties;
import com.example.demoapi.config.SecretProperties;

@SpringBootApplication
@EnableConfigurationProperties({AppProperties.class, SecretProperties.class})
public class DemoApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApiApplication.class, args);
    }
}
