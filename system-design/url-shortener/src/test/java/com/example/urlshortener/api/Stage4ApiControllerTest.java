package com.example.urlshortener.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class Stage4ApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void exposesAdvancedAnalyticsDimensions() throws Exception {
        String shortCode = createUrl("stage4-analytics-test");

        mockMvc.perform(get("/" + shortCode)
                .header("User-Agent", "Mozilla/5.0 AppleWebKit/537.36 Chrome/125.0 Safari/537.36")
                .header("Referer", "https://google.com/search?q=test")
                .header("CF-IPCountry", "PL"))
            .andExpect(status().isFound());

        mockMvc.perform(get("/api/v1/dashboard/urls/{shortCode}/analytics", shortCode))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shortCode").value(shortCode))
            .andExpect(jsonPath("$.totalClicks", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.topCountries[0].value").value("PL"))
            .andExpect(jsonPath("$.topBrowsers[0].value").value("chrome"));
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
