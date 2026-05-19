package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.domain.kotlin

// Service class that handles domain logic.
class PaymentService {

    fun describeResult(result: PaymentResult): String {
        // when expression works very well with sealed classes.
        // Kotlin knows all possible subclasses of PaymentResult.
        return when (result) {
            is PaymentResult.Success ->
                "Payment completed: ${result.transactionId}"

            is PaymentResult.Failure ->
                "Payment failed: ${result.reason}"
        }
    }
}