package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.scope_functions.java;

import pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.readability.java.User;

import java.util.List;

public class ScopeFunctionsExample {

    public User createAndPrintUser() {
        User user = new User(
                "John",
                "Doe",
                30,
                List.of("USER")
        );

        // Java usually uses explicit imperative steps.
        System.out.println("Created user: " + user.getFullName());

        int nameLength = user.getFullName().length();

        System.out.println("Full name length: " + nameLength);

        return user;
    }
}