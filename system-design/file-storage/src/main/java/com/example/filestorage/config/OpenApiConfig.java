package com.example.filestorage.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Publiczny kontrakt jest wersjonowany niezaleznie od implementacji kontrolerow. */
@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI fileStorageOpenApi() {
        String scheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info().title("File Storage API").version("v1")
                        .description("Metadata, resumable uploads, versions, sharing and sync"))
                .addSecurityItem(new SecurityRequirement().addList(scheme))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
