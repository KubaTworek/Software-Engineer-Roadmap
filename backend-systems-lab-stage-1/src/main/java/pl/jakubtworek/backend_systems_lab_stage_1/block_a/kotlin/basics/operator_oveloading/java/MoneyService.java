package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.operator_oveloading.java;

public class MoneyService {

    public Money calculateTotal() {
        Money first = new Money(100.0, "PLN");
        Money second = new Money(50.0, "PLN");

        // Java requires an explicit method call.
        return first.add(second);
    }
}