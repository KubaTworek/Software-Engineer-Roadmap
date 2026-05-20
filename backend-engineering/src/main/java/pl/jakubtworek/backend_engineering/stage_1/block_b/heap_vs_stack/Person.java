package pl.jakubtworek.backend_engineering.stage_1.block_b.heap_vs_stack;

public class Person {

    // Instance fields are part of the object.
    // Since the object lives on the heap, these fields live with it on the heap.
    String name;
    int age;

    public Person(String name, int age) {
        // Constructor parameters are local variables.
        // They live in the constructor's stack frame.
        this.name = name;
        this.age = age;
    }
}