package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.default_arguments.java;

import pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.readability.java.User;

import java.util.List;

public class UserFactory {

    public User createUser(String firstName, String lastName) {
        // Java has no default arguments.
        // Overloaded methods are commonly used instead.
        return createUser(firstName, lastName, 18, List.of("USER"));
    }

    public User createUser(String firstName, String lastName, int age) {
        return createUser(firstName, lastName, age, List.of("USER"));
    }

    public User createUser(String firstName, String lastName, int age, List<String> roles) {
        return new User(firstName, lastName, age, roles);
    }
}