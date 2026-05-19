package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.top_level_functions.java;

public class TaxService {

    public double calculateGrossPrice(double netPrice) {
        // Java calls a static method from a utility class.
        return netPrice + TaxUtils.calculateTax(netPrice);
    }
}