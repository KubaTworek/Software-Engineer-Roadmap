package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.comparing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComparatorTest {

    @Test
    void shouldSortByName() {

        List<Person> people = new ArrayList<>();

        people.add(new Person("Charlie", 40));
        people.add(new Person("Alice", 30));
        people.add(new Person("Bob", 20));

        people.sort(PersonComparators.BY_NAME);

        assertEquals("Alice", people.get(0).getName());
    }

    @Test
    void shouldSortByAgeDescending() {

        List<Person> people = new ArrayList<>();

        people.add(new Person("A", 20));
        people.add(new Person("B", 40));
        people.add(new Person("C", 30));

        people.sort(PersonComparators.BY_AGE_DESC);

        assertEquals(40, people.get(0).getAge());
    }
}