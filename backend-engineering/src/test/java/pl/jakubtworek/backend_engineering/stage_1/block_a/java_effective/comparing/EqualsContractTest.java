package pl.jakubtworek.backend_engineering.stage_1.block_a.java_effective.comparing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EqualsContractTest {

    @Test
    void shouldBeReflexive() {

        Money m = new Money(10, "USD");

        assertEquals(m, m);
    }

    @Test
    void shouldBeSymmetric() {

        Money a = new Money(10, "USD");
        Money b = new Money(10, "USD");

        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
    }

    @Test
    void shouldBeTransitive() {

        Money a = new Money(10, "USD");
        Money b = new Money(10, "USD");
        Money c = new Money(10, "USD");

        assertTrue(a.equals(b));
        assertTrue(b.equals(c));
        assertTrue(a.equals(c));
    }

    @Test
    void shouldNotEqualNull() {

        Money m = new Money(10, "USD");

        assertFalse(m.equals(null));
    }
}