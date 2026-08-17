package pl.jakubtworek.chatsystem.support;

import pl.jakubtworek.chatsystem.user.AppUser;
import pl.jakubtworek.chatsystem.user.UserRepository;

import java.util.UUID;

public final class TestUsers {
    private TestUsers() {}

    public static AppUser create(UserRepository userRepository, String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(new AppUser(
                prefix + "_" + suffix,
                prefix + "_" + suffix + "@example.com",
                "{noop}password",
                "User " + prefix
        ));
    }
}
