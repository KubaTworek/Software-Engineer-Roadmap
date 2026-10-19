package pl.jakubtworek.backend_engineering;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Uses fully qualified repository bean names because independent roadmap labs
 * intentionally contain repositories with the same short names.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "pl.jakubtworek.backend_engineering",
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class
)
public class JpaRepositoryConfig {
}
