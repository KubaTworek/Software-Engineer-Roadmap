package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.coroutines.async.java;

// UserProfile combines user data with additional information.
public class UserProfile {

    private final User user;
    private final String accountType;

    public UserProfile(User user, String accountType) {
        this.user = user;
        this.accountType = accountType;
    }

    public User getUser() {
        return user;
    }

    public String getAccountType() {
        return accountType;
    }
}