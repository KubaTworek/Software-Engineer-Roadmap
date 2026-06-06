package com.example.urlshortener.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShortUrlApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void createsAndRedirectsShortUrl() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"longUrl\":\"https://example.com/products?id=123\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.shortCode", matchesPattern("[A-Za-z0-9]+")))
            .andExpect(jsonPath("$.shortUrl").exists())
            .andReturn();

        String body = createResult.getResponse().getContentAsString();
        String shortCode = body.replaceAll(".*\\\"shortCode\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(get("/" + shortCode))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://example.com/products?id=123"));
    }

    @Test
    void createsShortUrlWithCustomAlias() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"longUrl\":\"https://example.com/campaign\",\"customAlias\":\"promo-2026\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.shortCode").value("promo-2026"))
            .andExpect(jsonPath("$.shortUrl").value("http://sho.rt/promo-2026"));

        mockMvc.perform(get("/promo-2026"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://example.com/campaign"));
    }

    @Test
    void rejectsDuplicateCustomAlias() throws Exception {
        String payload = "{\"longUrl\":\"https://example.com/a\",\"customAlias\":\"duplicate-alias\"}";

        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.replace("/a", "/b")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("CUSTOM_ALIAS_ALREADY_EXISTS"));
    }

    @Test
    void rejectsReservedCustomAlias() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"longUrl\":\"https://example.com\",\"customAlias\":\"api\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("RESERVED_ALIAS"));
    }

    @Test
    void rejectsInvalidUrl() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"longUrl\":\"file:///etc/passwd\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void returnsNotFoundForUnknownShortCode() throws Exception {
        mockMvc.perform(get("/unknown123"))
            .andExpect(status().isNotFound());
    }
}
