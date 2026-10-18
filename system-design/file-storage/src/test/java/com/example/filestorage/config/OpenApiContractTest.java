package com.example.filestorage.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiContractTest {
    @Test
    void contractDeclaresStableVersionAndBearerAuthentication() {
        var api = new OpenApiConfig().fileStorageOpenApi();

        assertThat(api.getInfo().getTitle()).isEqualTo("File Storage API");
        assertThat(api.getInfo().getVersion()).isEqualTo("v1");
        assertThat(api.getComponents().getSecuritySchemes().get("bearerAuth").getScheme())
                .isEqualTo("bearer");
    }
}
