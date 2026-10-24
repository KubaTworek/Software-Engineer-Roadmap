package pl.jakubtworek.lld.splitwise;

import java.util.Objects;

/**
 * Nazwa służy prezentacji; dwie osoby mogą mieć tę samą nazwę i różne ID.
 */
public record Member(MemberId id, String name) {

    public Member {
        Objects.requireNonNull(id, "id");
        name = Objects.requireNonNull(name, "name").strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Nazwa nie może być pusta");
        }
    }
}
