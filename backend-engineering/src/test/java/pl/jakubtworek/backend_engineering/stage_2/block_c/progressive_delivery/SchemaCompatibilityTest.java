package pl.jakubtworek.backend_engineering.stage_2.block_c.progressive_delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaCompatibilityTest {

    private final SchemaCompatibilityValidator validator = new SchemaCompatibilityValidator();
    private final SchemaCompatibilityValidator.ApplicationRevision oldRevision =
            new SchemaCompatibilityValidator.ApplicationRevision("v1", 1, 2);
    private final SchemaCompatibilityValidator.ApplicationRevision newRevision =
            new SchemaCompatibilityValidator.ApplicationRevision("v2", 2, 3);

    @Test
    void expandSchemaIsSupportedDuringMixedVersionRollout() {
        assertThat(validator.validate(2, List.of(oldRevision, newRevision))).isEmpty();
        assertThat(validator.canRollback(2, oldRevision)).isTrue();
    }

    @Test
    void contractMustWaitUntilOldRevisionIsGone() {
        assertThat(validator.validate(3, List.of(oldRevision, newRevision)))
                .containsExactly("v1 does not support schema 3");
        assertThat(validator.canRollback(3, oldRevision)).isFalse();
    }
}
