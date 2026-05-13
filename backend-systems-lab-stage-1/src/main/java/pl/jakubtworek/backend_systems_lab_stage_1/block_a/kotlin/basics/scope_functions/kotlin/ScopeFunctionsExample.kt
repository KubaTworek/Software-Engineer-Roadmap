package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.scope_functions.kotlin

import pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.readability.kotlin.User

class ScopeFunctionsExample {

    fun createAndPrintUser(): User {
        val user = User(
            firstName = "John",
            lastName = "Doe",
            age = 30,
            roles = listOf("USER")
        ).also {
            // also is useful for side effects,
            // such as logging or debugging.
            println("Created user: ${it.fullName}")
        }

        // let transforms the object into another value.
        val nameLength = user.fullName.let {
            it.length
        }

        println("Full name length: $nameLength")

        return user
    }
}