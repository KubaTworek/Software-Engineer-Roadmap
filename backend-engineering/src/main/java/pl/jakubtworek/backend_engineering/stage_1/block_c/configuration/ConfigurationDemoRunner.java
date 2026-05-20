package pl.jakubtworek.backend_engineering.stage_1.block_c.configuration;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Demonstrates active profiles and configuration loading.
 */
@Component
public class ConfigurationDemoRunner
        implements CommandLineRunner {

    private final Environment environment;
    private final ExternalBeanApiService externalBeanApiService;
    private final ValueBasedService valueBasedService;
    private final FeatureFlagsProperties featureFlags;

    public ConfigurationDemoRunner(
            Environment environment,
            ExternalBeanApiService externalBeanApiService,
            ValueBasedService valueBasedService,
            FeatureFlagsProperties featureFlags
    ) {
        this.environment = environment;
        this.externalBeanApiService = externalBeanApiService;
        this.valueBasedService = valueBasedService;
        this.featureFlags = featureFlags;
    }

    @Override
    public void run(String... args) {

        /**
         * Prints currently active profiles.
         */
        System.out.println(
                "Active profiles: "
                        + Arrays.toString(
                        environment.getActiveProfiles()
                )
        );

        /**
         * Demonstrates @ConfigurationProperties.
         */
        externalBeanApiService.printConfiguration();

        /**
         * Demonstrates @Value.
         */
        valueBasedService.printProperties();

        /**
         * Demonstrates feature flags.
         */
        System.out.println(
                "Registration enabled: "
                        + featureFlags.isRegistrationEnabled()
        );
    }
}