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

        // Always returns 1 regardless of the compared objects
        // This means every instance is considered "greater" than any other

        // Violates the antisymmetry rule:
        // if a.compareTo(b) > 0 then b.compareTo(a) should be < 0
        // here both calls return 1

        // Violates equality consistency:
        // even if two objects have the same value, the method never returns 0

        // This can break sorting algorithms and ordered collections
        // such as TreeSet or TreeMap

        return 1; // always "greater" -> incorrect implementation
    }
}