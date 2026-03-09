package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.streams;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LazyStreamExampleTest {

    @Test
    void intermediateOperationsShouldNotExecuteWithoutTerminalOperation() {

        AtomicInteger counter = new AtomicInteger();

        List<Integer> numbers = List.of(1, 2, 3);

        numbers.stream()
                .map(n -> {
                    counter.incrementAndGet();
                    return n * 2;
                });

        // brak operacji terminalnej → map nie wykona się

        assertEquals(0, counter.get());
    }

    @Test
    void streamOperationsShouldExecuteAfterTerminalOperation() {

        AtomicInteger counter = new AtomicInteger();

        List<Integer> numbers = List.of(1, 2, 3);

        numbers.stream()
                .map(n -> {
                    counter.incrementAndGet();
                    return n * 2;
                })
                .toList(); // terminal operation

        assertEquals(3, counter.get());
    }

}