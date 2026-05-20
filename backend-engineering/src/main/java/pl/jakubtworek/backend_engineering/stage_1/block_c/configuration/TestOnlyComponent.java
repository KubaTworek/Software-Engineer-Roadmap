package pl.jakubtworek.backend_engineering.stage_1.block_c.configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Bean available only in TEST profile.
 *
 * Useful for:
 * - mock integrations,
 * - fake external systems,
 * - testing utilities.
 */
@Component
@Profile("test")
public class TestOnlyComponent {

    @PostConstruct
    public void init() {

        System.out.println(
                "TEST profile bean initialized"
        );
    }
}