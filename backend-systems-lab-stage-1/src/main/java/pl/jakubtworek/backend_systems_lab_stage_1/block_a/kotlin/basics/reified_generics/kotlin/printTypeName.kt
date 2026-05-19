package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.reified_generics.kotlin

// Inline function with reified generic type.
// Kotlin can access the type T at runtime in this specific case.
inline fun <reified T> printTypeName(value: T) {
    println("Value: $value")
    println("Type: ${T::class.simpleName}")
}

class ReifiedExample {

    fun run() {
        // The type String is available inside printTypeName.
        printTypeName("Hello")

        // The type Int is available inside printTypeName.
        printTypeName(123)
    }
}