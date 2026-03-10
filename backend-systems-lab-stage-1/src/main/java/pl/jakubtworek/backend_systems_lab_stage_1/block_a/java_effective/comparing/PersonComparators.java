package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.comparing;

import java.util.Comparator;

/**
 * External comparison strategies.
 *
 * Comparator allows defining multiple orderings
 * without modifying the domain class.
 */
public class PersonComparators {

    /**
     * Comparator sorting by name.
     */
    public static final Comparator<Person> BY_NAME =
            // compares Person objects using the value returned by getName()
            // ordering will be lexicographical (String natural order)
            Comparator.comparing(Person::getName);

    /**
     * Comparator sorting by age descending.
     */
    public static final Comparator<Person> BY_AGE_DESC =
            // first creates comparator based on age
            Comparator.comparing(Person::getAge)
                    // reverses natural order -> oldest first
                    .reversed();

    /**
     * Comparator combining multiple fields.
     */
    public static final Comparator<Person> BY_NAME_THEN_AGE =
            // primary sorting key: name
            Comparator.comparing(Person::getName)
                    // if names are equal, compare by age
                    .thenComparing(Person::getAge);
}