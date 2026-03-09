package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.functional_interface;

/**
 * Functional Interface example.
 *
 * A functional interface has exactly one abstract method.
 *
 * Annotation @FunctionalInterface is optional but recommended.
 * Compiler verifies that only one abstract method exists.
 */
@FunctionalInterface
public interface Operation {

    int apply(int a, int b);

}