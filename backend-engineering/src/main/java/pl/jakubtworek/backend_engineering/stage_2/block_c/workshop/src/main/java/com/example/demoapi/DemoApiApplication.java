package pl.jakubtworek.backend_engineering.stage_2.block_c.workshop.src.main.java.com.example.demoapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import pl.jakubtworek.backend_engineering.stage_2.block_c.workshop.src.main.java.com.example.demoapi.config.AppProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class DemoApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApiApplication.class, args);
    }
}