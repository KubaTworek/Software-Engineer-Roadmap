package pl.jakubtworek.backend_engineering.stage_1.block_c.configuration;

import org.springframework.context.annotation.*;

/**
 * Bean active when NO profile is explicitly selected.
 *
 * Useful for local development defaults.
 */
@Configuration
@Profile("default")
public class DefaultConfig {

    @Bean
    public String defaultEnvironmentBean() {

        System.out.println("DEFAULT profile active");

        return "default-environment";
    }
}