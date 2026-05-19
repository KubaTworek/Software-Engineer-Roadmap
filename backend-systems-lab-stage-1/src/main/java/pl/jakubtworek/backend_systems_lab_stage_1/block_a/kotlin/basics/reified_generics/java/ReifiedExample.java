package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.reified_generics.java;

public class ReifiedExample {

    public void run() {
        GenericTypePrinter printer = new GenericTypePrinter();

        // Java requires passing Class<T> explicitly.
        printer.printTypeName("Hello", String.class);
        printer.printTypeName(123, Integer.class);
    }
}