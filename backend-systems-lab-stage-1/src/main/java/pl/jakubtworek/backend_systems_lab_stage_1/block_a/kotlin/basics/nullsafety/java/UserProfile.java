package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.nullsafety.java;

import java.util.Objects;

// The UserProfile class shows how Java handles nullable values.
// Java allows null by default, so the compiler does not prevent
// many null-related mistakes.
public class UserProfile {

    private final String username;

    // This field may be null, but Java does not express that clearly
    // unless we use annotations such as @Nullable.
    private final String email;

    private final Address address;

    public UserProfile(String username, String email, Address address) {
        // Objects.requireNonNull protects required values at runtime,
        // not at compile time.
        this.username = Objects.requireNonNull(username, "Username cannot be null");

        // Email is optional, so null is allowed here.
        this.email = email;

        // Address is also optional in this example.
        this.address = address;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public Address getAddress() {
        return address;
    }

    public String getEmailDomain() {
        // Without this null check, email.substring(...) could throw
        // a NullPointerException.
        if (email == null) {
            return "unknown";
        }

        return email.substring(email.indexOf("@") + 1);
    }

    public String getCityName() {
        // Java requires explicit null checks for nested nullable values.
        if (address == null) {
            return "unknown";
        }

        if (address.getCity() == null) {
            return "unknown";
        }

        return address.getCity();
    }
}
