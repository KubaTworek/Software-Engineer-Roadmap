package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.delegated_properties.kotlin

class ConfigurationService {

    // lazy is a delegated property.
    // The value is computed only when it is accessed for the first time.
    val configuration: String by lazy {
        println("Loading configuration...")
        "application-config"
    }

    fun printConfiguration() {
        println(configuration)
    }
}