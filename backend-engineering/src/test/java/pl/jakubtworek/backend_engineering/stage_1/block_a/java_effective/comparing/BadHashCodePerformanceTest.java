package pl.jakubtworek.backend_engineering.stage_1.block_a.java_effective.comparing;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BadHashCodePerformanceTest {

    @Test
    void manyCollisionsShouldStillWorkButBeSlow() {

        Set<BadHashMoney> set = new HashSet<>();

        for (int i = 0; i < 10000; i++) {
            set.add(new BadHashMoney(i, "USD"));
        }

        assertEquals(10000, set.size());
    }
}