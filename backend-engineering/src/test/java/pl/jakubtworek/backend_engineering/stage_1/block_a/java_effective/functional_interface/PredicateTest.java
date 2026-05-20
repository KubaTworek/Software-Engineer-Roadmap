package pl.jakubtworek.backend_engineering.stage_1.block_a.java_effective.functional_interface;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PredicateTest {

    @Test
    void predicateShouldValidateCondition() {

        boolean result =
                PredicateExample.test(10, v -> v > 5);

        assertTrue(result);
    }

    @Test
    void predicateShouldFailCondition() {

        boolean result =
                PredicateExample.test(2, v -> v > 5);

        assertFalse(result);
    }
}