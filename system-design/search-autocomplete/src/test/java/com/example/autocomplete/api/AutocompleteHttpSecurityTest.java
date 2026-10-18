package com.example.autocomplete.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

@SpringBootTest(properties = {
        "app.privacy.hmac-key=test-privacy-key-123456789",
        "app.security.operator.password={noop}test-password"
})
@AutoConfigureMockMvc
class AutocompleteHttpSecurityTest {
    @Autowired MockMvc mvc;

    @Test
    void servingPathIsPublicButControlPlaneRequiresOperator() throws Exception {
        mvc.perform(get("/api/v1/autocomplete").param("q", "iph").param("sessionId", "s1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions").isArray());

        mvc.perform(post("/api/v1/autocomplete/index/rollback"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/autocomplete/index/stats")
                        .with(httpBasic("operator", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").isNotEmpty());
    }

    @Test
    void rejectsOversizedQueryBeforeItReachesTheIndex() throws Exception {
        mvc.perform(get("/api/v1/autocomplete").param("q", "x".repeat(101)))
                .andExpect(status().isBadRequest());
    }
}
