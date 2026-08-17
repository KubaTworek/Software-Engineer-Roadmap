package com.example.ecommerce.config;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.auth.AppUserRepository;
import com.example.ecommerce.auth.UserRole;
import com.example.ecommerce.catalog.*;
import com.example.ecommerce.inventory.InventoryService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Set;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedData(
            AppUserRepository users,
            CategoryRepository categories,
            ProductRepository products,
            InventoryService inventory,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            if (!users.existsByEmailIgnoreCase("admin@example.com")) {
                users.save(new AppUser(
                        "admin@example.com",
                        passwordEncoder.encode("admin123"),
                        "Admin",
                        Set.of(UserRole.ADMIN, UserRole.CUSTOMER)
                ));
            }

            if (!users.existsByEmailIgnoreCase("customer@example.com")) {
                users.save(new AppUser(
                        "customer@example.com",
                        passwordEncoder.encode("customer123"),
                        "Customer",
                        Set.of(UserRole.CUSTOMER)
                ));
            }

            if (categories.count() == 0) {
                Category electronics = categories.save(new Category("Electronics", "electronics", null));
                Category books = categories.save(new Category("Books", "books", null));

                Product headphones = new Product(
                        "PROD-HEADPHONES",
                        "Wireless Headphones",
                        "wireless-headphones",
                        "Comfortable wireless headphones with active noise cancellation.",
                        "Acme",
                        electronics
                );
                ProductVariant headphonesBlack = new ProductVariant(
                        "VAR-HEADPHONES-BLACK",
                        "Black",
                        BigDecimal.valueOf(399.99),
                        "PLN"
                );
                headphones.addVariant(headphonesBlack);
                products.save(headphones);
                inventory.createInventoryItem(headphonesBlack, 25);

                Product book = new Product(
                        "PROD-CLEAN-CODE",
                        "Clean Code Handbook",
                        "clean-code-handbook",
                        "Practical handbook for writing maintainable software.",
                        "TechBooks",
                        books
                );
                ProductVariant paperback = new ProductVariant(
                        "VAR-CLEAN-CODE-PAPERBACK",
                        "Paperback",
                        BigDecimal.valueOf(129.99),
                        "PLN"
                );
                book.addVariant(paperback);
                products.save(book);
                inventory.createInventoryItem(paperback, 100);
            }
        };
    }
}
