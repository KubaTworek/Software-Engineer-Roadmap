package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.comparing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StreamSortingTest {

    @Test
    void shouldSortUsingStreamComparator() {

        List<Person> people = List.of(
                new Person("Bob", 20),
                new Person("Alice", 30),
                new Person("Charlie", 25)
        );

        List<Person> sorted =
                people.stream()
                        .sorted(PersonComparators.BY_NAME)
                        .toList();

        assertEquals("Alice", sorted.get(0).getName());
    }
}