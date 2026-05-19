package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.named_arguments.java;

import pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.readability.java.User;

import java.util.List;

public class NamedArgumentsExample {

    public User create() {
        // Java has no named arguments.
        // The meaning depends only on parameter order.
        return new User(
                "John",
                "Doe",
                30,
                List.of("USER", "ADMIN")
        );
    }
}