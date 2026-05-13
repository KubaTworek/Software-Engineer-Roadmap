package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.default_arguments.kotlin

import pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.readability.kotlin.User

class UserFactory {

    fun createUser(
        firstName: String,
        lastName: String,
        age: Int = 18,
        roles: List<String> = listOf("USER")
    ): User {
        // Default values reduce the need for overloaded methods.
        return User(
            firstName = firstName,
            lastName = lastName,
            age = age,
            roles = roles
        )
    }
}