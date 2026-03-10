package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.functional_interface;

/**
 * Functional interface representing a mathematical operation
 * performed on two integer values.
 *
 * Used for passing calculation logic (e.g. addition, subtraction)
 * to other classes such as Calculator.
 */
@FunctionalInterface
public interface Operation {

    /**
     * Performs an operation on two numbers.
     * The implementation can be provided using a lambda expression
     * or method reference.
     *
     * @param a first operand
     * @param b second operand
     * @return result of the operation
     */
    int apply(int a, int b);

}