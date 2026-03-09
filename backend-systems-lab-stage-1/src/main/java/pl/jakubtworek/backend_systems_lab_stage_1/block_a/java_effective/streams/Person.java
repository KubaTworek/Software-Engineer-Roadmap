package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.streams;

/**
 * Simple immutable model used in stream examples.
 *
 * Immutable objects are preferred in streams because:
 *  - no side effects
 *  - safe in parallel streams
 */
public final class Person {

    private final String name;
    private final int age;
    private final String city;

    public Person(String name, int age, String city) {
        this.name = name;
        this.age = age;
        this.city = city;
    }

    public String getName() { return name; }
    public int getAge() { return age; }
    public String getCity() { return city; }
}