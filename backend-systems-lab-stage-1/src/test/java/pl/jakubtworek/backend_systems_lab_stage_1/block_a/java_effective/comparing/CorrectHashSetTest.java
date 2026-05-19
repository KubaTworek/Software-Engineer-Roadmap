package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.comparing;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CorrectHashSetTest {

    @Test
    void hashSetShouldDetectDuplicates() {

        Set<Money> set = new HashSet<>();

        set.add(new Money(10, "USD"));
        set.add(new Money(10, "USD"));

        assertEquals(1, set.size());
    }
}