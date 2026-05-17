package pl.jakubtworek.backend_systems_lab_stage_1.block_c.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Example of @Value usage.
 *
 * Good for single simple properties.
 *
 * Less maintainable for large configuration groups.
 */
@Service
public class ValueBasedService {

    /**
     * Reads single property from configuration.
     */
    @Value("${app.name}")
    private String applicationName;

    /**
     * Default value syntax:
     * ${property:defaultValue}
     */
    @Value("${app.description:Default description}")
    private String description;

    public void printProperties() {

        System.out.println(
                "Application name: " + applicationName
        );

        System.out.println(
                "Description: " + description
        );
    }
}