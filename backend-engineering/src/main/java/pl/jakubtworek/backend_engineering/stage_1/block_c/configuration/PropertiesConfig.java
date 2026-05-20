package pl.jakubtworek.backend_engineering.stage_1.block_c.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers @ConfigurationProperties classes.
 *
 * Spring Boot automatically binds values
 * from application.yml/properties.
 */
@Configuration
@EnableConfigurationProperties({
        ExternalApiProperties.class,
        FeatureFlagsProperties.class
})
public class PropertiesConfig {
}