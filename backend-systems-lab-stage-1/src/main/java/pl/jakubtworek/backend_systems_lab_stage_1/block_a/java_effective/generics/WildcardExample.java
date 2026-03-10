package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.generics;

import java.util.List;

/**
 * Example showing two common wildcard usages:
 * reading values with an upper bound and
 * adding values with a lower bound.
 */
public class WildcardExample {

    /**
     * Accepts a list of elements that are Number or any subclass of Number
     * (e.g. Integer, Double, Float).
     */
    public static double sum(List<? extends Number> numbers) {

        double result = 0;

        // Elements can be safely read as Number
        // because every element extends Number
        for (Number n : numbers) {
            result += n.doubleValue();
        }

        return result;
    }

    /**
     * Accepts a list whose element type is Integer
     * or any superclass of Integer (e.g. Number, Object).
     */
    public static void addNumbers(List<? super Integer> list) {

        // It is safe to add Integer values because
        // the list is guaranteed to accept Integer or its supertypes
        list.add(1);
        list.add(2);
    }
}