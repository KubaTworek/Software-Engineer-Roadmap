package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.readability.kotlin

// Kotlin allows the same domain model
// to be expressed with significantly less boilerplate.
data class User(

    // Properties are declared directly
    // inside the primary constructor.
    val firstName: String,
    val lastName: String,
    val age: Int,
    val roles: List<String>
) {

    // Validation can be placed inside the init block.
    init {
        require(firstName.isNotBlank()) {
            "First name cannot be empty"
        }

        require(age >= 0) {
            "Age cannot be negative"
        }
    }

    // Computed property instead of explicit getter method.
    val fullName: String
        get() = "$firstName $lastName"

    // Business logic method.
    fun isAdmin(): Boolean {
        return "ADMIN" in roles
    }
}