package pl.jakubtworek.lld.workflow.domain;

import java.util.Objects;
import java.util.Set;

/**
 * Zaufany kontekst wykonawcy polecenia. Kopia ról chroni przed ich zmianą przez
 * klienta w trakcie operacji. W aplikacji sieciowej Actor musi budować adapter
 * bezpieczeństwa, a nie dowolny użytkownik na podstawie pola formularza.
 */
public record Actor(UserId id, Set<Role> roles) {

    public Actor {
        Objects.requireNonNull(id);
        roles = Set.copyOf(roles);
    }

    public boolean has(Role role) {
        return roles.contains(role);
    }
}
