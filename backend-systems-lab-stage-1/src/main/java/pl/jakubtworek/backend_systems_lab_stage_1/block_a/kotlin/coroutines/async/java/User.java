package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.coroutines.async.java;

// Simple Java domain class.
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