package com.example.observability.server.region;

import com.example.observability.server.repository.TelemetryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Serwis obsługujący funkcje multi-region.
 *
 * W tej aplikacji multi-region jest warstwą operatorską Fazy 3.
 * Nie wykonuje realnej replikacji danych samodzielnie.
 *
 * Ta klasa:
 * - pokazuje topologię regionów,
 * - zapisuje heartbeat replikacji,
 * - ocenia health replikacji dla tenanta,
 * - buduje prosty plan failover.
 *
 * Faktyczny transport danych między regionami musiałby być realizowany osobnym procesem,
 * np. replikacją object storage, Kafka MirrorMakerem, ClickHouse replication
 * albo dedykowanym replication workerem.
 */
@Service
public class MultiRegionService {

    /**
     * Konfiguracja multi-region.
     *
     * Zawiera m.in.:
     * - tryb pracy, np. single-region, active-passive, active-active,
     * - nazwę aktualnego regionu,
     * - listę peer regionów,
     * - flagę włączenia replikacji,
     * - maksymalny zdrowy lag replikacji.
     */
    private final MultiRegionProperties properties;

    /**
     * Repozytorium używane do zapisu i odczytu eventów replikacji.
     *
     * MultiRegionService nie trzyma stanu w pamięci.
     * Health replikacji opiera się na ostatnich eventach zapisanych w storage.
     */
    private final TelemetryRepository repository;

    public MultiRegionService(
            MultiRegionProperties properties,
            TelemetryRepository repository
    ) {
        this.properties = properties;
        this.repository = repository;
    }

    /**
     * Zwraca aktualną topologię multi-region.
     *
     * Używane przez endpoint:
     * GET /api/v1/phase3/regions/topology
     *
     * To jest widok globalny platformy:
     * - jaki jest bieżący region,
     * - jakie są regiony peer,
     * - czy replikacja jest włączona,
     * - jaki lag uznajemy za zdrowy.
     */
    public Topology topology() {
        return new Topology(
                properties.getMode(),
                properties.getCurrent(),
                properties.getPeers(),
                properties.isReplicationEnabled(),
                properties.getMaxHealthyLagMs()
        );
    }

    /**
     * Ocenia zdrowie replikacji dla danego tenanta.
     *
     * Mechanizm:
     * 1. Pobiera ostatnie eventy replikacji z repository.
     * 2. Sprawdza, czy którykolwiek stream ma zbyt duży lag.
     * 3. Sprawdza, czy którykolwiek stream ma status failed.
     * 4. Jeśli replikacja jest wyłączona, status = disabled.
     *
     * Wynik:
     * - healthy: wszystkie ostatnie streamy wyglądają dobrze,
     * - degraded: co najmniej jeden stream ma zbyt duży lag albo failed,
     * - disabled: replikacja wyłączona w konfiguracji.
     *
     * To jest health oparty o heartbeat/eventy, nie aktywne testowanie regionu.
     */
    public ReplicationHealth health(String tenantId) {
        List<ReplicationStream> streams = repository.latestReplicationEvents(tenantId);

        String status = streams
                .stream()
                .anyMatch(s ->
                        s.lagMs() > properties.getMaxHealthyLagMs()
                                || "failed".equalsIgnoreCase(s.status())
                )
                ? "degraded"
                : "healthy";

        if (!properties.isReplicationEnabled()) {
            status = "disabled";
        }

        return new ReplicationHealth(
                tenantId,
                status,
                Instant.now(),
                streams
        );
    }

    /**
     * Zapisuje heartbeat replikacji dla konkretnego streamu.
     *
     * Używane przez endpoint:
     * POST /api/v1/phase3/regions/replication/heartbeat
     *
     * Parametry opisują stan replikacji:
     * - tenantId: którego tenanta dotyczy replikacja,
     * - targetRegion: region docelowy,
     * - streamName: np. logs, metrics, traces, object-storage,
     * - lagMs: opóźnienie replikacji,
     * - status: np. ok, degraded, failed,
     * - details: opis diagnostyczny.
     *
     * sourceRegion jest brany z konfiguracji aktualnego regionu.
     */
    public void heartbeat(
            String tenantId,
            String targetRegion,
            String streamName,
            long lagMs,
            String status,
            String details
    ) {
        repository.insertReplicationEvent(
                tenantId,
                properties.getCurrent(),
                targetRegion,
                streamName,
                lagMs,
                status,
                details
        );
    }

    /**
     * Buduje prosty plan failover dla tenanta.
     *
     * Obecna implementacja nie wybiera najlepszego regionu automatycznie.
     * Dla każdego peer regionu zwraca kandydaturę:
     * - region,
     * - akcję promote-read-write,
     * - guardrail manual-approval-required.
     *
     * To oznacza:
     * system sugeruje możliwe regiony awaryjne,
     * ale nie wykonuje automatycznego failovera.
     *
     * To jest bezpieczne dla MVP, bo failover może skutkować:
     * - utratą danych, jeśli replika ma lag,
     * - split-brain,
     * - problemami z routingiem ingest/query.
     */
    public List<FailoverCandidate> failoverPlan(String tenantId) {
        List<FailoverCandidate> out = new ArrayList<>();

        for (String peer : properties.getPeers()) {
            out.add(new FailoverCandidate(
                    peer,
                    "promote-read-write",
                    "manual-approval-required"
            ));
        }

        return out;
    }

    /**
     * Globalny opis topologii multi-region.
     *
     * mode:
     * - tryb pracy, np. single-region / active-passive / active-active.
     *
     * currentRegion:
     * - region, w którym działa bieżąca instancja.
     *
     * peers:
     * - inne regiony znane tej instancji.
     *
     * replicationEnabled:
     * - czy replikacja jest logicznie włączona.
     *
     * maxHealthyLagMs:
     * - maksymalny lag, który nadal uznajemy za zdrowy.
     */
    public record Topology(
            String mode,
            String currentRegion,
            List<String> peers,
            boolean replicationEnabled,
            long maxHealthyLagMs
    ) {
    }

    /**
     * Wynik oceny zdrowia replikacji dla tenanta.
     *
     * status:
     * - healthy,
     * - degraded,
     * - disabled.
     *
     * streams:
     * - ostatnie znane eventy replikacyjne.
     */
    public record ReplicationHealth(
            String tenantId,
            String status,
            Instant checkedAt,
            List<ReplicationStream> streams
    ) {
    }

    /**
     * Status pojedynczego streamu replikacji.
     *
     * Przykłady streamName:
     * - logs,
     * - metrics,
     * - traces,
     * - object-storage,
     * - metadata.
     */
    public record ReplicationStream(
            String sourceRegion,
            String targetRegion,
            String streamName,
            long lagMs,
            String status,
            Instant eventTime,
            String details
    ) {
    }

    /**
     * Kandydat do failover.
     *
     * region:
     * - region, który może przejąć ruch.
     *
     * action:
     * - sugerowana akcja operatorska.
     *
     * guardrail:
     * - zabezpieczenie wymagane przed wykonaniem akcji.
     */
    public record FailoverCandidate(
            String region,
            String action,
            String guardrail
    ) {
    }
}