package pl.jakubtworek.backend_engineering.stage_1.block_c.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Simulates Spring Cloud Config usage.
 *
 * Values may come from:
 * - Git repository,
 * - Config Server,
 * - Vault,
 * - external environment variables.
 */
@Component
public class CloudConfigClient {

    /**
     * Property can be externally managed
     * by Spring Cloud Config Server.
     */
    @Value("${external.service.url}")
    private String serviceUrl;

    public void printCloudConfig() {

        System.out.println(
                "External service URL: " + serviceUrl
        );
    }
}