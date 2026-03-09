package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.comparing;

/**
 * Example domain object.
 *
 * Implements Comparable to define natural ordering.
 */
public class Person implements Comparable<Person> {

    private final String name;
    private final int age;

    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() { return name; }

    public int getAge() { return age; }

    /**
     * Natural ordering implementation.
     *
     * Here we define ordering by age.
     */
    @Override
    public int compareTo(Person other) {

        return Integer.compare(this.age, other.age);
    }

    @Override
    public String toString() {
        return name + " (" + age + ")";
    }
}