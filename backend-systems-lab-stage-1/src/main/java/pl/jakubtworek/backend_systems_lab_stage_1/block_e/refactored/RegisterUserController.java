// src/main/java/com/example/registration/api/RegisterUserController.java
package pl.jakubtworek.backend_systems_lab_stage_1.block_e.refactored;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/users")
public class RegisterUserController {

    private final RegisterUserUseCase useCase;

    public RegisterUserController(RegisterUserUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public ResponseEntity<RegisterUserOutput> register(@RequestBody RegisterUserInput input) {
        RegisterUserOutput output = useCase.handle(input);
        return ResponseEntity.status(HttpStatus.CREATED).body(output);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, String>> onValidation(ValidationException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Map<String, String>> onDuplicate(DuplicateEmailException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
