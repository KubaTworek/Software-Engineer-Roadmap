package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.reified_generics.java;

public class GenericTypePrinter {

    public <T> void printTypeName(T value, Class<T> type) {
        // Java generics use type erasure.
        // Because of that, the type must often be passed explicitly.
        System.out.println("Value: " + value);
        System.out.println("Type: " + type.getSimpleName());
    }
}