package pl.jakubtworek.marketplace.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

/** Audytuje akcję i podmiot, ale nigdy payload, token, sekret ani PII klienta. */
@Component
public class SecurityAuditLog {
    private static final Logger log = LoggerFactory.getLogger(SecurityAuditLog.class);
    public void dlqReplay(String actor, UUID dlqId) {
        log.info("security_audit action=DLQ_REPLAY actor={} resourceId={} at={}", actor, dlqId, Instant.now());
    }
}
