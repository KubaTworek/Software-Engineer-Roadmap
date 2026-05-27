package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Przykład dokumentu profilu użytkownika.
 *
 * Ten model pokazuje dane, które często są odczytywane razem:
 * - podstawowe dane użytkownika,
 * - adresy,
 * - preferencje,
 * - tagi używane do segmentacji.
 */
public class UserProfileDocument {

    private final String id;
    private final String email;
    private final String displayName;
    private final List<AddressDocument> addresses;
    private final Map<String, String> preferences;
    private final List<String> tags;
    private final Instant createdAt;
    private final Instant lastLoginAt;

    public UserProfileDocument(
            String id,
            String email,
            String displayName,
            List<AddressDocument> addresses,
            Map<String, String> preferences,
            List<String> tags,
            Instant createdAt,
            Instant lastLoginAt
    ) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.addresses = List.copyOf(addresses);
        this.preferences = Map.copyOf(preferences);
        this.tags = List.copyOf(tags);
        this.createdAt = createdAt;
        this.lastLoginAt = lastLoginAt;
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public String preference(String key) {
        return preferences.get(key);
    }

    public String id() { return id; }
    public String email() { return email; }
    public String displayName() { return displayName; }
    public List<AddressDocument> addresses() { return addresses; }
    public Map<String, String> preferences() { return preferences; }
    public List<String> tags() { return tags; }
    public Instant createdAt() { return createdAt; }
    public Instant lastLoginAt() { return lastLoginAt; }

    public record AddressDocument(
            String type,
            String country,
            String city,
            String street,
            String postalCode
    ) {
    }
}
