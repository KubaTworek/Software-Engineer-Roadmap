package pl.jakubtworek.backend_systems_lab_stage_1.block_c.configuration;

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