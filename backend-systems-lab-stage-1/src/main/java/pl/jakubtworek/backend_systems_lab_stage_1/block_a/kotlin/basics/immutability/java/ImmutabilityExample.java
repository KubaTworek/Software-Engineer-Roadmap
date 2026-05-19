package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.immutability.java;

import pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.readability.java.User;

import java.util.List;

public class ImmutabilityExample {

    public User createUser() {
        // final prevents reassignment of the variable.
        final String firstName = "John";

        // Regular Java variables are mutable by default.
        int age = 25;
        age += 1;

        // List.of creates an immutable list.
        List<String> roles = List.of("USER", "ADMIN");

        return new User(
                firstName,
                "Doe",
                age,
                roles
        );
    }
}