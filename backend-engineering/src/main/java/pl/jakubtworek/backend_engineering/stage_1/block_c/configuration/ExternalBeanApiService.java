package pl.jakubtworek.backend_engineering.stage_1.block_c.configuration;

import org.springframework.stereotype.Service;

/**
 * Service using strongly-typed configuration.
 */
@Service
public class ExternalBeanApiService {

    private final ValidatedExternalApiProperties properties;

    public ExternalBeanApiService(
            ValidatedExternalApiProperties properties
    ) {
        this.properties = properties;
    }

    /**
     * Reads configuration loaded from application.yml.
     */
    public void printConfiguration() {

        System.out.println(
                "API URL: " + properties.baseUrl()
        );

        System.out.println(
                "Timeout: " + properties.timeout()
        );
    }
}
