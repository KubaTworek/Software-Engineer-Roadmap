package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.immutability.kotlin

import pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.readability.kotlin.User

class ImmutabilityExample {

    fun createUser(): User {
        // val creates a read-only reference.
        // It cannot be reassigned after initialization.
        val firstName = "John"

        // Mutable reference.
        // It can be reassigned.
        var age = 25
        age += 1

        // listOf creates a read-only list interface.
        val roles = listOf("USER", "ADMIN")

        return User(
            firstName = firstName,
            lastName = "Doe",
            age = age,
            roles = roles
        )
    }
}