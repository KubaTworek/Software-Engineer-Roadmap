package com.example.videostreaming.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigurationValidatorTest {
    @Test
    void productionFailsClosedForDemonstrationSecrets() {
        var validator = new ProductionConfigurationValidator(
                "dev-secret-change-me", "dev-signed-cookie-secret-change-me", "minioadmin", false, true);

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rejects demo secrets");
    }

    @Test
    void productionAcceptsExternalSecretsAndSecureCookies() {
        var validator = new ProductionConfigurationValidator(
                "a-real-secret-from-a-secret-manager", "a-different-playback-signing-secret", "service-account", true, false);

        assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
    }

    @Test
    void productionRejectsDemoAdministratorEvenWithStrongPasswordConfiguredElsewhere() {
        var validator = new ProductionConfigurationValidator(
                "a-real-secret-from-a-secret-manager", "a-different-playback-signing-secret", "service-account", true, true);

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("demonstration administrator");
    }
}
