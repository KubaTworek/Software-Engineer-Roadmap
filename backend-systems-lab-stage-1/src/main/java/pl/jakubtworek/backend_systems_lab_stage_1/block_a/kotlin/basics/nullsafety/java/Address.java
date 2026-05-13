package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.nullsafety.java;

// A simple Address class used by UserProfile.
public class Address {

    // City may be null, but this is not visible in the type system.
    private final String city;

    public Address(String city) {
        this.city = city;
    }

    public String getCity() {
        return city;
    }
}