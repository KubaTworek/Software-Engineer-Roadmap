package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.comparing;

/**
 * Example domain object.
 *
 * Implements Comparable to define natural ordering.
 */
public class Person implements Comparable<Person> {

    // immutable fields describing the person
    private final String name;
    private final int age;

    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    /**
     * Natural ordering implementation.
     *
     * Objects are compared only by age.
     */
    @Override
    public int compareTo(Person other) {

        // uses Integer.compare to avoid overflow
        // returns:
        // negative value  -> this person is younger
        // zero            -> same age
        // positive value  -> this person is older
        return Integer.compare(this.age, other.age);
    }

    @Override
    public String toString() {

        // simple readable representation useful for logging or debugging
        return name + " (" + age + ")";
    }
}