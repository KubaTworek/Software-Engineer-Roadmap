package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.streams;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Repository returning Optional instead of null.
 *
 * Why Optional?
 *
 * - communicates that value may be missing
 * - avoids NullPointerException
 * - forces caller to handle absence
 */
public class PersonRepository {

    private final Map<Integer, Person> users = new HashMap<>();

    public PersonRepository() {
        users.put(1, new Person("Alice", 16, "NY"));
        users.put(2, new Person("Bob", 42, "LA"));
    }

    public Optional<Person> findById(int id) {

        return Optional.ofNullable(users.get(id));
    }
}