package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.streams;

import java.util.ArrayList;
import java.util.List;

/**
 * Example comparing a stream pipeline with side effects
 * and a version that relies on stream collectors.
 */
public class SideEffectExample {

    public List<Integer> brokenExample(List<Integer> numbers) {

        // External mutable container that will be modified
        // from inside the stream pipeline.
        List<Integer> result = new ArrayList<>();

        numbers.stream()

                // Each element is transformed by multiplying it by 2.
                .map(n -> n * 2)

                // The result of the mapping is added to the external list.
                // This mutates shared state outside the stream pipeline.
                // With parallelStream() this could lead to race conditions.
                .forEach(result::add); // side effect

        // The populated list is returned after stream execution finishes.
        return result;
    }

    /**
     * Version without modifying external state.
     */
    public List<Integer> correctExample(List<Integer> numbers) {

        return numbers.stream()

                // Same transformation as in the previous method.
                .map(n -> n * 2)

                // Collects all transformed elements into a new list
                // created internally by the stream framework.
                .toList();
    }
}