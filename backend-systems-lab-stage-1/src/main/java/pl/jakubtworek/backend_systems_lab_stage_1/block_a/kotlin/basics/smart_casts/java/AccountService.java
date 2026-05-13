package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.smart_casts.java;

public class AccountService {

    public void printAccountInfo(Account account) {
        if (account instanceof AdminUserAccount admin) {
            // Modern Java supports pattern matching for instanceof.
            // Older Java versions required explicit casting.
            System.out.println(admin.getPermissions());
        }

        if (account instanceof RegularUserAccount regularUser) {
            System.out.println(regularUser.getName());
        }
    }
}