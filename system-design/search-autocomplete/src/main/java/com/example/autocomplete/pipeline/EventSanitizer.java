package com.example.autocomplete.pipeline;

import com.example.autocomplete.privacy.QueryPrivacyService;
import org.springframework.stereotype.Component;

@Component
public class EventSanitizer {
    private final QueryPrivacyService privacyService;

    public EventSanitizer(QueryPrivacyService privacyService) {
        this.privacyService = privacyService;
    }

    public QueryEvent sanitize(QueryEvent event) {
        return new QueryEvent(
                privacyService.hashUserId(event.userId()),
                event.sessionId(),
                privacyService.redactPii(event.query()),
                event.locale(),
                event.country(),
                privacyService.hashIp(event.clientIp()),
                event.timestamp()
        );
    }
}
