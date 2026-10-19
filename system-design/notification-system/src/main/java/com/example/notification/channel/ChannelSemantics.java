package com.example.notification.channel;

import java.util.*;

/**
 * Wykonywalne laboratoria semantyki kanałów. Nie są adapterami SDK providerów:
 * modelują decyzje, które wspólny pipeline musi podjąć inaczej dla każdego medium.
 */
public final class ChannelSemantics {
    private ChannelSemantics() {}

    public enum TrafficDecision { ALLOW, THROTTLE, PAUSE }

    /**
     * Reputacja email jest mechanizmem sterującym ruchem. Complaint jest silniejszym
     * sygnałem niż soft bounce, dlatego nawet mała wartość zatrzymuje kampanię.
     */
    public static TrafficDecision emailDecision(long sent, long hardBounces, long complaints, boolean dmarcAligned) {
        if (sent <= 0 || hardBounces < 0 || complaints < 0) throw new IllegalArgumentException("Invalid counters");
        double bounceRate = hardBounces / (double) sent;
        double complaintRate = complaints / (double) sent;
        if (!dmarcAligned || complaintRate >= 0.001 || bounceRate >= 0.05) return TrafficDecision.PAUSE;
        if (bounceRate >= 0.02) return TrafficDecision.THROTTLE;
        return TrafficDecision.ALLOW;
    }

    public record SmsEncoding(String alphabet, int segments, int charactersPerSegment) {}

    /**
     * SMS jest rozliczany w segmentach. GSM-7 mieści 160 znaków w jednym segmencie
     * i 153 po połączeniu; Unicode odpowiednio 70 i 67. To uproszczony, jawny model:
     * pełny GSM 03.38 ma znaki escape liczone podwójnie.
     */
    public static SmsEncoding smsEncoding(String body) {
        Objects.requireNonNull(body, "body");
        boolean gsm7 = body.chars().allMatch(c -> c >= 32 && c <= 126);
        int single = gsm7 ? 160 : 70;
        int multipart = gsm7 ? 153 : 67;
        int segments = body.length() <= single ? 1 : (int) Math.ceil(body.length() / (double) multipart);
        return new SmsEncoding(gsm7 ? "GSM-7" : "UCS-2", segments, segments == 1 ? single : multipart);
    }

    /** Token push jest własnością urządzenia, nie użytkownika; rotacja zastępuje token atomowo. */
    public static final class PushTokenRegistry {
        private final Map<String, String> activeByDevice = new HashMap<>();
        public void register(String tenant, String user, String device, String token) {
            activeByDevice.put(key(tenant, user, device), Objects.requireNonNull(token));
        }
        public void invalidate(String tenant, String user, String device, String observedToken) {
            activeByDevice.remove(key(tenant, user, device), observedToken);
        }
        public Optional<String> active(String tenant, String user, String device) {
            return Optional.ofNullable(activeByDevice.get(key(tenant, user, device)));
        }
        private String key(String tenant, String user, String device) { return tenant + ":" + user + ":" + device; }
    }

    /**
     * Projekcja unread toleruje replay dzięki eventId. Operacja mark-read jest monotoniczna:
     * ponowienie tego samego eventu nie może zmniejszyć licznika drugi raz.
     */
    public static final class InAppUnreadProjection {
        private final Set<UUID> applied = new HashSet<>();
        private final Set<UUID> unread = new HashSet<>();
        public int delivered(UUID eventId, UUID messageId) {
            if (applied.add(eventId)) unread.add(messageId);
            return unread.size();
        }
        public int read(UUID eventId, UUID messageId) {
            if (applied.add(eventId)) unread.remove(messageId);
            return unread.size();
        }
    }
}
