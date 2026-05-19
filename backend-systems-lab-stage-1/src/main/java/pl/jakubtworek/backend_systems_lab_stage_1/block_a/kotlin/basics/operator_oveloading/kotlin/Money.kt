package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.operator_oveloading.kotlin

data class Money(
    val amount: Double,
    val currency: String
) {
    // Kotlin supports operator overloading.
    // This allows using the + operator for domain objects.
    operator fun plus(other: Money): Money {
        require(currency == other.currency) {
            "Currencies must be the same"
        }

        return Money(
            amount = amount + other.amount,
            currency = currency
        )
    }
}

class MoneyService {

    fun calculateTotal(): Money {
        val first = Money(100.0, "PLN")
        val second = Money(50.0, "PLN")

        // This calls the overloaded plus function.
        return first + second
    }
}