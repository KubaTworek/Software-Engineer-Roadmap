package com.example.urlshortener.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    private Limit create = new Limit(10, Duration.ofMinutes(1));
    private Limit redirect = new Limit(600, Duration.ofMinutes(1));

    public Limit getCreate() {
        return create;
    }

    public void setCreate(Limit create) {
        this.create = create;
    }

    public Limit getRedirect() {
        return redirect;
    }

    public void setRedirect(Limit redirect) {
        this.redirect = redirect;
    }

    public static class Limit {
        private long requests;
        private Duration window;

        public Limit() {
        }

        public Limit(long requests, Duration window) {
            this.requests = requests;
            this.window = window;
        }

        public long getRequests() {
            return requests;
        }

        public void setRequests(long requests) {
            this.requests = requests;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }
}
