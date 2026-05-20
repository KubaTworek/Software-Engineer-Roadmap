package pl.jakubtworek.backend_engineering.stage_1.block_c.configuration;

import org.springframework.context.annotation.*;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration active only for DEV profile.
 *
 * Bean will exist only when:
 * spring.profiles.active=dev
 */
@Configuration
@Profile("dev")
public class DevConfig {

    /**
     * Development-only bean.
     */
    @Bean
    public RestTemplate devRestTemplate() {

        System.out.println("DEV configuration loaded");

        return new RestTemplate();
    }
}