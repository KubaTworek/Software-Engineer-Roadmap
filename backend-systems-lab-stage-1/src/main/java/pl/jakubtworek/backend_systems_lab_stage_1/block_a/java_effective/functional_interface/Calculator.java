package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.functional_interface;

/**
 * Demonstrates usage of functional interface with lambda expressions.
 */
public class Calculator {

    public int calculate(int a, int b, Operation operation) {

        return operation.apply(a, b);
    }

}