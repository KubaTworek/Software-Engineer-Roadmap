package com.example.videostreaming.premium;

import com.example.videostreaming.auth.User;
import com.example.videostreaming.catalog.Video;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Serwis sprawdzający uprawnienia użytkownika do treści premium.
 *
 * Główna odpowiedzialność:
 * - ustala aktualny plan subskrypcji użytkownika,
 * - porównuje plan użytkownika z wymaganym planem filmu,
 * - uwzględnia wynik geo-blockingu,
 * - zwraca jedną spójną decyzję dostępu dla playbacku i DRM.
 *
 * Używany m.in. przez:
 * - PlaybackController przed wydaniem URL-i do odtwarzania,
 * - DrmLicenseController przed wydaniem licencji DRM.
 */
@Service
public class EntitlementService {

    /**
     * Repozytorium subskrypcji użytkowników.
     *
     * Służy do znalezienia najlepszej aktywnej subskrypcji użytkownika
     * w danym momencie.
     */
    private final UserSubscriptionRepository subscriptions;

    public EntitlementService(UserSubscriptionRepository subscriptions) {
        this.subscriptions = subscriptions;
    }

    /**
     * Zwraca aktualny plan subskrypcji użytkownika.
     *
     * Jeśli użytkownik ma aktywną subskrypcję, zwracany jest jej plan.
     * Jeśli nie ma żadnej aktywnej subskrypcji, użytkownik dostaje plan FREE.
     *
     * Instant.now() oznacza, że liczą się tylko subskrypcje aktywne
     * w bieżącym momencie.
     *
     * @param userId identyfikator użytkownika
     * @return aktualny plan użytkownika
     */
    public SubscriptionPlanCode currentPlan(UUID userId) {
        return subscriptions.findBestActiveForUser(userId, Instant.now())
                .map(UserSubscription::getPlanCode)
                .orElse(SubscriptionPlanCode.FREE);
    }

    /**
     * Sprawdza, czy użytkownik może obejrzeć dany film.
     *
     * Decyzja składa się z dwóch głównych warunków:
     * 1. Film musi być dostępny w regionie użytkownika.
     * 2. Plan użytkownika musi spełniać minimalny plan wymagany przez film.
     *
     * Kolejność ma sens biznesowy:
     * - jeśli treść nie jest dostępna regionalnie, od razu odmawiamy,
     * - dopiero potem sprawdzamy subskrypcję.
     *
     * @param user aktualnie zalogowany użytkownik
     * @param video film, który użytkownik chce odtworzyć
     * @param geoAllowed wynik geo-blockingu policzony wcześniej przez GeoService
     * @return decyzja dostępu z powodem, planami i informacją o DRM
     */
    public EntitlementDecision check(User user, Video video, boolean geoAllowed) {
        /*
         * Pobieramy aktualny plan użytkownika.
         *
         * Jeśli użytkownik nie ma aktywnej subskrypcji,
         * currentPlan zwróci FREE.
         */
        SubscriptionPlanCode userPlan = currentPlan(user.getId());

        /*
         * Minimalny plan wymagany przez film.
         *
         * Przykłady:
         * - FREE: każdy zalogowany użytkownik,
         * - BASIC: użytkownik z BASIC lub PREMIUM,
         * - PREMIUM: tylko użytkownik z PREMIUM.
         */
        SubscriptionPlanCode requiredPlan = video.getMinimumPlanCode();

        /*
         * Geo-blocking ma pierwszeństwo.
         *
         * Nawet użytkownik z najlepszym planem nie może obejrzeć filmu,
         * jeśli treść nie jest licencjonowana w jego regionie.
         */
        if (!geoAllowed) {
            return new EntitlementDecision(
                    false,
                    "Content is not available in this region",
                    userPlan,
                    requiredPlan,
                    video.isDrmProtected(),
                    false
            );
        }

        /*
         * Sprawdzamy, czy plan użytkownika spełnia wymaganie filmu.
         *
         * Logika porównania jest ukryta w userPlan.satisfies(requiredPlan),
         * dzięki czemu tutaj nie musimy znać hierarchii planów.
         */
        if (!userPlan.satisfies(requiredPlan)) {
            return new EntitlementDecision(
                    false,
                    "Subscription plan is not sufficient",
                    userPlan,
                    requiredPlan,
                    video.isDrmProtected(),
                    true
            );
        }

        /*
         * Użytkownik przeszedł wszystkie checki:
         * - region jest dozwolony,
         * - plan subskrypcji jest wystarczający.
         */
        return new EntitlementDecision(
                true,
                "OK",
                userPlan,
                requiredPlan,
                video.isDrmProtected(),
                true
        );
    }

    /**
     * Wynik sprawdzenia uprawnień do filmu.
     *
     * allowed:
     * - czy użytkownik może odtworzyć treść.
     *
     * reason:
     * - czytelny powód decyzji, używany np. w błędzie 403.
     *
     * userPlan:
     * - aktualny plan użytkownika.
     *
     * requiredPlan:
     * - minimalny plan wymagany przez film.
     *
     * drmRequired:
     * - czy film wymaga DRM.
     *
     * geoAllowed:
     * - czy region użytkownika jest dozwolony.
     */
    public record EntitlementDecision(
            boolean allowed,
            String reason,
            SubscriptionPlanCode userPlan,
            SubscriptionPlanCode requiredPlan,
            boolean drmRequired,
            boolean geoAllowed
    ) {}
}