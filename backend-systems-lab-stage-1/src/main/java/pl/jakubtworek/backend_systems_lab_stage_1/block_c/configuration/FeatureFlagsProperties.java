package pl.jakubtworek.backend_systems_lab_stage_1.block_c.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Example feature flags configuration.
 *
 * Useful for enabling/disabling features
 * depending on environment.
 */
@ConfigurationProperties(prefix = "app.features")
public class FeatureFlagsProperties {

    /**
     * Example:
     * app.features.registration-enabled=true
     */
    private boolean registrationEnabled;

    /**
     * Example:
     * app.features.experimental-ui=false
     */
    private boolean experimentalUi;

    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    public void setRegistrationEnabled(boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }

    public boolean isExperimentalUi() {
        return experimentalUi;
    }

    public void setExperimentalUi(boolean experimentalUi) {
        this.experimentalUi = experimentalUi;
    }
}