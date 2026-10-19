package com.example.observability.server.ingest;

import com.example.observability.server.model.LogIngestRequest;
import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Minimalna warstwa fail-safe przed zapisaniem typowych sekretów i PII. */
@Service
public class SensitiveDataRedactor {
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[a-z0-9._~+/-]+=*");
    private static final Pattern EMAIL = Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private static final Set<String> SENSITIVE_KEYS = Set.of("authorization", "token", "password", "secret", "api_key");

    public void redact(LogIngestRequest request) {
        for (var log : request.getLogs()) {
            String message = log.getMessage() == null ? "" : log.getMessage();
            log.setMessage(EMAIL.matcher(BEARER.matcher(message).replaceAll("Bearer [REDACTED]"))
                    .replaceAll("[EMAIL_REDACTED]"));
            var safe = new LinkedHashMap<String, Object>();
            if (log.getAttributes() != null) log.getAttributes().forEach((key, value) ->
                    safe.put(key, SENSITIVE_KEYS.contains(key.toLowerCase(Locale.ROOT)) ? "[REDACTED]" : value));
            log.setAttributes(safe);
        }
    }
}
