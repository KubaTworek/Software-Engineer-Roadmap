package pl.jakubtworek.backend_engineering.stage_1.block_c.exception;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * Demonstrates method-level validation in service layer.
 *
 * @Validated enables validation of method parameters
 * in Spring-managed beans.
 */
@Service
@Validated
public class ProductService {

    /**
     * Method parameter validation.
     *
     * If validation fails, ConstraintViolationException is thrown.
     */
    public void createProduct(
            @NotBlank(message = "Product name is required") String name,
            @Min(value = 1, message = "Price must be positive") int price
    ) {
        System.out.println("Product created");
    }
}