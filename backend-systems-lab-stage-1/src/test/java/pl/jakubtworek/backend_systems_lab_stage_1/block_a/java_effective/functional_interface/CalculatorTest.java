package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.functional_interface;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CalculatorTest {

    @Test
    void lambdaShouldImplementFunctionalInterface() {

        Calculator calculator = new Calculator();

        int result = calculator.calculate(
                2,
                3,
                (a, b) -> a + b
        );

        assertEquals(5, result);
    }

    @Test
    void lambdaMultiplicationExample() {

        Calculator calculator = new Calculator();

        int result = calculator.calculate(
                4,
                5,
                (a, b) -> a * b
        );

        assertEquals(20, result);
    }
}