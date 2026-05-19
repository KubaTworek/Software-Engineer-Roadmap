package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.functional_interface;

/**
 * Simple wrapper class showing how a functional interface can be passed
 * as a parameter and executed inside a method.
 */
public class Calculator {

    /**
     * Executes a mathematical operation provided as a lambda expression
     * or method reference.
     *
     * @param a first operand
     * @param b second operand
     * @param operation implementation of Operation functional interface
     *                  (e.g. lambda like (x, y) -> x + y)
     * @return result of the provided operation
     */
    public int calculate(int a, int b, Operation operation) {

        // Delegates the actual calculation logic to the functional interface
        return operation.apply(a, b);
    }

}