package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.streams;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PersonStreamServiceTest {

    private final PersonStreamService service =
            new PersonStreamService();

    private final List<Person> people = List.of(
            new Person("Alice", 30, "Warsaw"),
            new Person("Bob", 40, "Berlin"),
            new Person("Charlie", 20, "Warsaw")
    );

    @Test
    void shouldFilterPeopleByAge() {

        List<Person> result =
                service.findOlderThan(people, 25);

        assertEquals(2, result.size());
    }

    @Test
    void shouldExtractNames() {

        List<String> names =
                service.extractNames(people);

        assertTrue(names.contains("Alice"));
    }

    @Test
    void shouldSumAges() {

        int sum = service.sumAges(people);

        assertEquals(90, sum);
    }

    @Test
    void shouldGroupByCity() {

        Map<String, List<Person>> map =
                service.groupByCity(people);

        assertEquals(2, map.get("Warsaw").size());
    }

    @Test
    void shouldFindFirstFromCity() {

        var person =
                service.findFirstFromCity(people, "Berlin");

        assertTrue(person.isPresent());
        assertEquals("Bob", person.get().getName());
    }
}