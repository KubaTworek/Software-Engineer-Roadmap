package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.named_arguments.kotlin

import pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.readability.kotlin.User

class NamedArgumentsExample {

    fun create(): User {
        // Named arguments make method calls easier to read,
        // especially when there are many parameters of the same type.
        return User(
            firstName = "John",
            lastName = "Doe",
            age = 30,
            roles = listOf("USER", "ADMIN")
        )
    }
}