package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.top_level_functions.kotlin

// Top-level function.
// Kotlin allows functions outside classes.
fun calculateTax(amount: Double): Double {
    return amount * 0.23
}

class TaxService {

    fun calculateGrossPrice(netPrice: Double): Double {
        // Top-level functions can be called directly.
        return netPrice + calculateTax(netPrice)
    }
}