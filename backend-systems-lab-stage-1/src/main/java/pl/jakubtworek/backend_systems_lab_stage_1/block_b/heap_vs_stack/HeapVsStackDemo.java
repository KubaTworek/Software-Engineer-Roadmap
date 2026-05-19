package pl.jakubtworek.backend_systems_lab_stage_1.block_b.heap_vs_stack;

public class HeapVsStackDemo {

    public static void main(String[] args) {
        // 'primitiveValue' is a local primitive variable.
        // Its value lives in the current stack frame.
        int primitiveValue = 42;

        // 'personRef' is a local reference variable.
        // The reference itself lives in the current stack frame.
        // The actual Person object lives on the heap.
        Person personRef = new Person("Alice", 30);

        // A copy of the reference is passed to the method.
        // Both references point to the same heap object.
        modifyPerson(personRef);

        System.out.println(personRef.name); // Bob

        // A copy of the primitive value is passed to the method.
        // The original stack variable is not changed.
        modifyPrimitive(primitiveValue);

        System.out.println(primitiveValue); // 42
    }

    private static void modifyPerson(Person person) {
        // 'person' is another local reference on this method's stack frame.
        // It points to the same Person object on the heap.
        person.name = "Bob";

        // This creates a new String object / string reference behavior,
        // but the important point is: we mutate the heap object via reference.
    }

    private static void modifyPrimitive(int value) {
        // 'value' is a local copy in this method's stack frame.
        // Changing it does not affect the original variable in main().
        value = 100;
    }
}