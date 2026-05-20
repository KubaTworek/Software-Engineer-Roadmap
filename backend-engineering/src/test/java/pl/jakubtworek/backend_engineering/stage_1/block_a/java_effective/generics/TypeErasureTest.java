package pl.jakubtworek.backend_engineering.stage_1.block_a.java_effective.generics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TypeErasureTest {

    @Test
    void listsShouldHaveSameRuntimeType() {

        List<String> a = List.of("A");
        List<Integer> b = List.of(1);

        assertTrue(TypeErasureExample.compareLists(a, b));
    }
}