package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.streams;

import java.util.Optional;

/**
 * Demonstrates Optional API usage.
 */
public class PersonService {

    private final PersonRepository repository;

    public PersonService(PersonRepository repository) {
        this.repository = repository;
    }

    /**
     * map example
     *
     * transforms Optional<User> into Optional<String>
     */
    public Optional<String> findPersonName(int id) {

        return repository.findById(id)
                .map(Person::getName);
    }

    /**
     * orElse example
     */
    public String findPersonNameOrDefault(int id) {

        return repository.findById(id)
                .map(Person::getName)
                .orElse("Unknown");
    }

    /**
     * orElseThrow example
     */
    public String findPersonNameOrThrow(int id) {

        return repository.findById(id)
                .map(Person::getName)
                .orElseThrow(() ->
                        new IllegalArgumentException("Person not found"));
    }

    /**
     * flatMap example
     */
    public Optional<String> findUpperCaseName(int id) {

        return repository.findById(id)
                .flatMap(user ->
                        Optional.of(user.getName().toUpperCase()));
    }
}