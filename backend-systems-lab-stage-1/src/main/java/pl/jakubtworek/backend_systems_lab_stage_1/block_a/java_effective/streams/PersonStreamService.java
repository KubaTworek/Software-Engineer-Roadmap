package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.streams;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service class showing several typical operations performed on
 * a collection of Person objects using stream pipelines.
 */
public class PersonStreamService {

    /**
     * Returns a list of people whose age is greater than the provided value.
     */
    public List<Person> findOlderThan(List<Person> people, int age) {

        return people.stream()

                // Keeps only elements that satisfy the predicate.
                // Each Person is checked individually.
                .filter(p -> p.getAge() > age)

                // Terminal operation that collects remaining elements
                // into a new immutable List.
                .toList();
    }

    /**
     * Extracts only the names from the list of Person objects.
     */
    public List<String> extractNames(List<Person> people) {

        return people.stream()

                // Transforms each Person object into a String
                // containing the person's name.
                .map(Person::getName)

                // Collects the mapped values into a List<String>.
                .toList();
    }

    /**
     * Calculates the sum of all ages in the collection.
     */
    public int sumAges(List<Person> people) {

        return people.stream()

                // Converts each Person to their age value.
                .map(Person::getAge)

                // Combines all age values into a single result.
                // 0 is the identity value and Integer::sum is the accumulator.
                .reduce(0, Integer::sum);
    }

    /**
     * Groups people by the city they belong to.
     */
    public Map<String, List<Person>> groupByCity(List<Person> people) {

        return people.stream()

                // Collector that builds a Map where:
                // key   -> city name
                // value -> list of people living in that city
                .collect(Collectors.groupingBy(Person::getCity));
    }

    /**
     * Finds the first person from a given city.
     */
    public Optional<Person> findFirstFromCity(List<Person> people, String city) {

        return people.stream()

                // Filters people whose city matches the given value.
                .filter(p -> p.getCity().equals(city))

                // Returns the first matching element wrapped in Optional.
                // If no match exists, Optional.empty() is returned.
                .findFirst();
    }
}