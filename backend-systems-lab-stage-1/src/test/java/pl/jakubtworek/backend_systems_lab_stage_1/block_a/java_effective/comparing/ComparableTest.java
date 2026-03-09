package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.comparing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComparableTest {

    @Test
    void shouldSortUsingNaturalOrdering() {

        List<Person> people = new ArrayList<>();

        people.add(new Person("Alice", 30));
        people.add(new Person("Bob", 20));
        people.add(new Person("Charlie", 40));

        people.sort(null); // uses Comparable

        assertEquals(20, people.get(0).getAge());
        assertEquals(30, people.get(1).getAge());
        assertEquals(40, people.get(2).getAge());
    }
}