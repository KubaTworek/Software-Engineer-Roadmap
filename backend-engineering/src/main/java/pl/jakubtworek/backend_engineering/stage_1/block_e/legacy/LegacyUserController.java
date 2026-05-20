package pl.jakubtworek.backend_engineering.stage_1.block_e.legacy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/legacy/users")
public class LegacyUserController {

    @Autowired
    private UserService userService;

    @PostMapping
    public ResponseEntity<?> register(
            @RequestBody UserDto dto,
            @RequestParam(defaultValue = "true") boolean sendWelcomeEmail) {

        LegacyUserEntity saved = userService.registerUser(dto, sendWelcomeEmail);
        if (saved == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "registration failed"));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", saved.getId()));
    }
}
