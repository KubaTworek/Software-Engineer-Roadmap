package pl.jakubtworek.backend_systems_lab_stage_1.block_c.configuration;


/**
 * Example business service.
 *
 * Registered manually via @Bean in AppConfig.
 */
public class UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    public UserService(
            UserRepository userRepository,
            EmailService emailService
    ) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    public void registerUser(String email) {

        System.out.println(
                "Registering user: " + email
        );

        emailService.sendWelcomeEmail(email);
    }
}