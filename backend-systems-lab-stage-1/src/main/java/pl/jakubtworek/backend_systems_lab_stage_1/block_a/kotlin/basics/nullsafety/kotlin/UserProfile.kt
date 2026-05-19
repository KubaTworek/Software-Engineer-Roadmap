package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.nullsafety.kotlin

// The UserProfile class shows Kotlin's null safety.
// Kotlin makes nullable and non-nullable types explicit.
data class UserProfile(

    // Non-nullable String.
    // This property cannot be null.
    val username: String,

    // Nullable String.
    // The question mark means this value may be null.
    val email: String?,

    // Nullable Address.
    val address: Address?
) {

    fun getEmailDomain(): String {
        // Safe call operator ?. calls substringAfter only if email is not null.
        // Elvis operator ?: returns "unknown" when the left side is null.
        return email?.substringAfter("@") ?: "unknown"
    }

    fun getCityName(): String {
        // Safe calls can be chained for nested nullable values.
        return address?.city ?: "unknown"
    }
}
