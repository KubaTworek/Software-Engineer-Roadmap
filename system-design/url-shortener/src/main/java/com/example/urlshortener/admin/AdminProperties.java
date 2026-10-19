package com.example.urlshortener.admin;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin")
public class AdminProperties {
    private String token;
    private List<String> previousTokens = new ArrayList<>();

    public AdminProperties() {}
    public AdminProperties(String token) { this.token = token; }
    public String token() { return token; }
    public void setToken(String token) { this.token = token; }
    public List<String> previousTokens() { return List.copyOf(previousTokens); }
    public void setPreviousTokens(List<String> values) {
        previousTokens = values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
}
