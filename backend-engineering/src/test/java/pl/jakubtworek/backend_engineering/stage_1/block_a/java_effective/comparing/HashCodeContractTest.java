package pl.jakubtworek.backend_engineering.stage_1.block_a.java_effective.comparing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HashCodeContractTest {

    @Test
    void equalObjectsMustHaveSameHashCode() {

        Money a = new Money(10, "USD");
        Money b = new Money(10, "USD");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}