package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.coroutines.parallel.java;

// Simple domain model representing a user.
public class User {

    private final String id;
    private final String name;

    public User(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}