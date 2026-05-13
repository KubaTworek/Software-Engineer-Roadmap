package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.nullsafety.kotlin

// A simple Address class used by UserProfile.
data class Address(

    // Non-nullable city.
    // Kotlin does not allow null here unless we explicitly write String?.
    val city: String
)