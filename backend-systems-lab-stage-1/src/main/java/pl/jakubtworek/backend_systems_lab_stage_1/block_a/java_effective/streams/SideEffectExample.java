package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.streams;

import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates why side effects in streams are dangerous.
 *
 * Mutating shared state inside stream
 * breaks functional paradigm and parallel safety.
 */
public class SideEffectExample {

    public List<Integer> brokenExample(List<Integer> numbers) {

        List<Integer> result = new ArrayList<>();

        numbers.stream()
                .map(n -> n * 2)
                .forEach(result::add); // side effect

        return result;
    }

    /**
     * Correct version using collector.
     */
    public List<Integer> correctExample(List<Integer> numbers) {

        return numbers.stream()
                .map(n -> n * 2)
                .toList();
    }
}