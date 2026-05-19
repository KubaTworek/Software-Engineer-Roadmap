package pl.jakubtworek.backend_systems_lab_stage_1.block_c.configuration;

import org.springframework.stereotype.Repository;

/**
 * Repository bean automatically registered
 * by component scanning.
 */
@Repository
public class UserRepository {

    public void saveUser(String email) {

        System.out.println(
                "Saving user: " + email
        );
    }
}