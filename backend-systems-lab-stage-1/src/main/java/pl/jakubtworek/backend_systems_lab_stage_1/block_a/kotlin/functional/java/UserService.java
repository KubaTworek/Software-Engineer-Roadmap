package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.functional.java;

import pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.readability.java.User;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// The UserService class shows functional-style programming in Java.
// Java supports functional programming mainly through Streams,
// lambdas, and method references.
public class UserService {

    private final List<User> users;

    public UserService(List<User> users) {
        this.users = users;
    }

    public List<String> getAdultUserNames() {
        // Stream creates a processing pipeline.
        // filter keeps only users who match the condition.
        // map transforms User objects into String values.
        // toList collects the result into a new list.
        return users.stream()
                .filter(user -> user.getAge() >= 18)
                .map(User::getFullName)
                .toList();
    }

    public Map<String, List<User>> groupUsersByRole() {
        // flatMap is needed because each user can have many roles.
        // This example creates pairs of role -> user,
        // then groups users by role.
        return users.stream()
                .flatMap(user -> user.getRoles().stream()
                        .map(role -> Map.entry(role, user)))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));
    }

    public boolean hasAnyAdmin() {
        // anyMatch returns true if at least one user is an admin.
        return users.stream()
                .anyMatch(user -> user.getRoles().contains("ADMIN"));
    }
}