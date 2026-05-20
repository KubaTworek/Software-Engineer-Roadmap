package pl.jakubtworek.backend_engineering.stage_1.block_a.java_effective.streams;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PersonServiceTest {

    private final PersonService service =
            new PersonService(new PersonRepository());

    @Test
    void shouldReturnUserName() {

        var name = service.findPersonName(1);

        assertTrue(name.isPresent());
        assertEquals("Alice", name.get());
    }

    @Test
    void shouldReturnDefaultIfMissing() {

        String name = service.findPersonNameOrDefault(99);

        assertEquals("Unknown", name);
    }

    @Test
    void shouldThrowExceptionIfMissing() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.findPersonNameOrThrow(99)
        );
    }

    @Test
    void shouldTransformUsingMap() {

        var name = service.findUpperCaseName(1);

        assertEquals("ALICE", name.get());
    }
}