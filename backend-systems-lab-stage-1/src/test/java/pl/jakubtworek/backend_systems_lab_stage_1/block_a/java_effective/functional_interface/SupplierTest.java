package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.functional_interface;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SupplierTest {

    @Test
    void supplierShouldProvideValue() {

        String value =
                SupplierExample.get(() -> "generated");

        assertEquals("generated", value);
    }
}