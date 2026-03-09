package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.functional_interface;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MethodReferenceTest {

    @Test
    void methodReferenceShouldWork() {

        String result =
                MethodReferenceExample.apply(
                        MethodReferenceExample::convert,
                        10
                );

        assertEquals("10", result);
    }
}