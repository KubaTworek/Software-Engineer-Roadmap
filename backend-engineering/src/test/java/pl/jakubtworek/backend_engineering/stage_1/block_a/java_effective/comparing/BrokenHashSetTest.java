package pl.jakubtworek.backend_engineering.stage_1.block_a.java_effective.comparing;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BrokenHashSetTest {

    @Test
    void hashSetShouldFailWithoutHashCode() {

        Set<BrokenMoney> set = new HashSet<>();

        set.add(new BrokenMoney(10, "USD"));
        set.add(new BrokenMoney(10, "USD"));

        // equals says objects are equal
        // but HashSet still stores both

        assertEquals(2, set.size());
    }
}