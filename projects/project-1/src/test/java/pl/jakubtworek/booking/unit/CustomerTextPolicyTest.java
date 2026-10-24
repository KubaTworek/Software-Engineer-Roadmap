package pl.jakubtworek.booking.unit;
import org.junit.jupiter.api.Test;
import pl.jakubtworek.booking.entity.Customer;
import static org.junit.jupiter.api.Assertions.*;
class CustomerTextPolicyTest {
    @Test void normalizesDisplayNameWithoutFoldingEmailIdentity() {
        var customer = new Customer(" Jose\u0301 ", "Name@example.test");
        assertEquals("José", customer.getFullName());
        assertEquals("Name@example.test", customer.getEmail());
        assertThrows(IllegalArgumentException.class, () -> new Customer("x\uD800", "x@example.test"));
        assertThrows(IllegalArgumentException.class, () -> new Customer("x\nInjected", "x@example.test"));
    }
}
