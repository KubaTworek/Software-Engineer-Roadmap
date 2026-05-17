package pl.jakubtworek.backend_systems_lab_stage_1.block_c.configuration;

import org.springframework.stereotype.Service;

/**
 * Service using strongly-typed configuration.
 */
@Service
public class ExternalApiService {

    private final ExternalApiProperties properties;

    public ExternalApiService(
            ExternalApiProperties properties
    ) {
        this.properties = properties;
    }

    /**
     * Reads configuration loaded from application.yml.
     */
    public void printConfiguration() {

        System.out.println(
                "API URL: " + properties.getBaseUrl()
        );

        System.out.println(
                "Timeout: " + properties.getTimeout()
        );
    }
}