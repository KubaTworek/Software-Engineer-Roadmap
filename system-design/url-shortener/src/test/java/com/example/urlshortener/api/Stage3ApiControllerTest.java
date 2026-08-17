package com.example.urlshortener.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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
class Stage3ApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void blocksAndUnblocksUrl() throws Exception {
        String shortCode = createUrl("stage3-block-test");

        mockMvc.perform(post("/api/v1/admin/urls/{shortCode}/block", shortCode)
                .header("X-Admin-Token", "test-admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"malware suspicion\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("BLOCKED"))
            .andExpect(jsonPath("$.blockedReason").value("malware suspicion"));

        mockMvc.perform(get("/" + shortCode))
            .andExpect(status().isGone());

        mockMvc.perform(post("/api/v1/admin/urls/{shortCode}/unblock", shortCode)
                .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/" + shortCode))
            .andExpect(status().isFound());
    }

    @Test
    void rejectsAdminCallWithoutToken() throws Exception {
        String shortCode = createUrl("stage3-token-test");

        mockMvc.perform(post("/api/v1/admin/urls/{shortCode}/block", shortCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"abuse\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void exposesDashboardAnalytics() throws Exception {
        String shortCode = createUrl("stage3-analytics-test");

        mockMvc.perform(get("/" + shortCode))
            .andExpect(status().isFound());

        Thread.sleep(500);

        mockMvc.perform(get("/api/v1/dashboard/urls/{shortCode}/analytics", shortCode))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shortCode").value(shortCode))
            .andExpect(jsonPath("$.totalClicks", greaterThanOrEqualTo(1)));
    }

    private String createUrl(String alias) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"longUrl\":\"https://example.com/" + alias + "\",\"customAlias\":\"" + alias + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();

        String body = createResult.getResponse().getContentAsString();
        return body.replaceAll(".*\\\"shortCode\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }
}
