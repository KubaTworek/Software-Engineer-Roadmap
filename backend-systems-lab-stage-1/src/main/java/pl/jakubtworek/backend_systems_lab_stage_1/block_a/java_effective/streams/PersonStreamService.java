package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.streams;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Demonstrates common Stream API operations.
 *
 * Streams are:
 *  - declarative
 *  - lazy
 *  - functional
 *
 * Stream pipeline consists of:
 *   source -> intermediate operations -> terminal operation
 */
public class PersonStreamService {

    /**
     * FILTER example
     *
     * Returns people older than given age.
     */
    public List<Person> findOlderThan(List<Person> people, int age) {

        return people.stream()
                .filter(p -> p.getAge() > age)
                .toList();
    }

    /**
     * MAP example
     *
     * Transform objects.
     */
    public List<String> extractNames(List<Person> people) {

        return people.stream()
                .map(Person::getName)
                .toList();
    }

    /**
     * REDUCE example
     *
     * Aggregates values.
     */
    public int sumAges(List<Person> people) {

        return people.stream()
                .map(Person::getAge)
                .reduce(0, Integer::sum);
    }

    /**
     * GROUPING example
     */
    public Map<String, List<Person>> groupByCity(List<Person> people) {

        return people.stream()
                .collect(Collectors.groupingBy(Person::getCity));
    }

    /**
     * FIND example returning Optional.
     */
    public Optional<Person> findFirstFromCity(List<Person> people, String city) {

        return people.stream()
                .filter(p -> p.getCity().equals(city))
                .findFirst();
    }
}