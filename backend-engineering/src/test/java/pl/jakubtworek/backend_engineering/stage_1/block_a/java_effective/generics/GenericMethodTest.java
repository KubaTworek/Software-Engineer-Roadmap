package pl.jakubtworek.backend_engineering.stage_1.block_a.java_effective.generics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GenericMethodTest {

    @Test
    void swapShouldExchangeElements() {

        Integer[] arr = {1, 2, 3};

        GenericUtils.swap(arr, 0, 2);

        assertEquals(3, arr[0]);
        assertEquals(1, arr[2]);
    }
}