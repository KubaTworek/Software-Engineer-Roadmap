package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.streams;

import java.util.Optional;

/**
 * Service class demonstrating several typical operations performed on Optional.
 */
public class PersonService {

    private final PersonRepository repository;

    public PersonService(PersonRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the name of a person wrapped in Optional.
     */
    public Optional<String> findPersonName(int id) {

        return repository.findById(id)

                // If a Person is present, extract the name
                // and wrap it automatically into Optional<String>.
                // If the Optional is empty, map is skipped.
                .map(Person::getName);
    }

    /**
     * Returns the person's name or a default value.
     */
    public String findPersonNameOrDefault(int id) {

        return repository.findById(id)

                // Transform Optional<Person> into Optional<String>
                .map(Person::getName)

                // If no value is present, return the default string.
                .orElse("Unknown");
    }

    /**
     * Returns the person's name or throws an exception if not found.
     */
    public String findPersonNameOrThrow(int id) {

        return repository.findById(id)

                // Extract name if the person exists.
                .map(Person::getName)

                // If the Optional is empty, create and throw the exception.
                .orElseThrow(() ->
                        new IllegalArgumentException("Person not found"));
    }

    /**
     * Example using flatMap with Optional.
     */
    public Optional<String> findUpperCaseName(int id) {

        return repository.findById(id)

                // flatMap is used when the mapping function itself
                // returns an Optional. It prevents creating Optional<Optional<String>>.
                .flatMap(user ->
                        Optional.of(user.getName().toUpperCase()));
    }
}