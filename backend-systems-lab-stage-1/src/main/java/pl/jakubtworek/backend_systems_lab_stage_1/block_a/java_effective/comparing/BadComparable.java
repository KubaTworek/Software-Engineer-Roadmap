package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.comparing;

/**
 * Bad Comparable implementation.
 *
 * Violates compareTo contract.
 */
public class BadComparable implements Comparable<BadComparable> {

    private final int value;

    public BadComparable(int value) {
        this.value = value;
    }

    @Override
    public int compareTo(BadComparable o) {

        return 1; // always greater -> incorrect
    }
}