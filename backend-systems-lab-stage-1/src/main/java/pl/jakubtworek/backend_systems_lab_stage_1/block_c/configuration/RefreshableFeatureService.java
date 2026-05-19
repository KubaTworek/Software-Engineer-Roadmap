package pl.jakubtworek.backend_systems_lab_stage_1.block_c.configuration;

import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

/**
 * Bean supporting runtime configuration refresh.
 *
 * Requires:
 * - Spring Cloud Config,
 * - /actuator/refresh endpoint.
 *
 * After refresh:
 * bean is recreated with new configuration values.
 */
@Service
@RefreshScope
public class RefreshableFeatureService {

    /**
     * Example runtime-refreshable feature.
     */
    public void executeFeature() {

        System.out.println(
                "Feature executed with refreshed config"
        );
    }
}