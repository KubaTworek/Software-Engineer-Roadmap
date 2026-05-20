package pl.jakubtworek.backend_engineering.stage_1.block_a.java_effective.comparing;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class TreeSetComparatorTest {

    @Test
    void treeSetUsesComparatorForUniqueness() {

        Set<Person> set =
                new TreeSet<>(PersonComparators.BY_NAME);

        set.add(new Person("Alice", 20));
        set.add(new Person("Alice", 30));

        // comparator treats them as equal
        assertEquals(1, set.size());
    }
}