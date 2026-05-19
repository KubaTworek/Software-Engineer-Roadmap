package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.extension_functions.kotlin

// Extension function.
// It adds behavior to String without inheritance
// and without creating a utility class.
fun String.isEmail(): Boolean {
    return contains("@") && contains(".")
}

class EmailValidator {

    fun validate(email: String): Boolean {
        // Looks like a regular method on String,
        // but it is actually an extension function.
        return email.isEmail()
    }
}