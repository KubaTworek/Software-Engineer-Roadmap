package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.functional_interface;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FunctionTest {

    @Test
    void functionShouldTransformValue() {

        String result =
                FunctionExample.transform(
                        10,
                        n -> "Value: " + n
                );

        assertEquals("Value: 10", result);
    }
}