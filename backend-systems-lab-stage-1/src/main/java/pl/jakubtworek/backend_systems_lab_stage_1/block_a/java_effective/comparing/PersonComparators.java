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
            Comparator.comparing(Person::getName);

    /**
     * Comparator sorting by age descending.
     */
    public static final Comparator<Person> BY_AGE_DESC =
            Comparator.comparing(Person::getAge)
                      .reversed();

    /**
     * Comparator combining multiple fields.
     */
    public static final Comparator<Person> BY_NAME_THEN_AGE =
            Comparator.comparing(Person::getName)
                      .thenComparing(Person::getAge);
}