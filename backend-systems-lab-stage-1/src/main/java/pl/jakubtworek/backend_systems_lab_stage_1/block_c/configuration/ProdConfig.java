package pl.jakubtworek.backend_systems_lab_stage_1.block_c.configuration;

import org.springframework.context.annotation.*;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration active only for PROD profile.
 *
 * Separate configuration per environment
 * is common in enterprise systems.
 */
@Configuration
@Profile("prod")
public class ProdConfig {

    /**
     * Production-only bean.
     */
    @Bean
    public RestTemplate prodRestTemplate() {

        System.out.println("PROD configuration loaded");

        return new RestTemplate();
    }
}