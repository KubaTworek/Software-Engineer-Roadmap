package pl.jakubtworek.backend_engineering.stage_1.block_a.java_effective.generics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoxTest {

    @Test
    void genericBoxShouldStoreValue() {

        Box<String> box = new Box<>();

        box.set("Hello");

        assertEquals("Hello", box.get());
    }

    @Test
    void genericBoxShouldWorkWithDifferentTypes() {

        Box<Integer> box = new Box<>();

        box.set(42);

        assertEquals(42, box.get());
    }
}