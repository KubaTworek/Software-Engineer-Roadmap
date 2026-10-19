package pl.jakubtworek.backend_engineering.stage_3.block_b;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.incident.IncidentHop;
import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.incident.IncidentSignal;
import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.incident.IncidentTriageDecision;
import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.incident.IncidentTriageEngine;
import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.runbook.CheckoutRunbooks;
import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.runbook.IncidentType;
import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.runbook.Runbook;
import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.runbook.RunbookMarkdownRenderer;
import pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.runbook.RunbookStep;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class IncidentTriageAndRunbookTest {

    private final IncidentTriageEngine triage = new IncidentTriageEngine();

    @Test
    void derivesRedisRunbookFromTheSignalWhenNoHopWasIdentifiedYet() {
        IncidentTriageDecision decision = triage.decide(
                true,
                true,
                IncidentSignal.REDIS_ERRORS,
                IncidentHop.NO_CLEAR_HOP
        );

        assertThat(decision.suspectedHop()).isEqualTo(IncidentHop.REDIS);
        assertThat(decision.recommendedRunbook()).isEqualTo(IncidentType.REDIS_DOWN);
    }

    @Test
    void derivesDatabaseRunbookFromTimeoutSignal() {
        IncidentTriageDecision decision = triage.decide(
                true,
                true,
                IncidentSignal.DATABASE_TIMEOUTS,
                IncidentHop.NO_CLEAR_HOP
        );

        assertThat(decision.suspectedHop()).isEqualTo(IncidentHop.DATABASE);
        assertThat(decision.recommendedRunbook()).isEqualTo(IncidentType.DB_DOWN);
    }

    @Test
    void doesNotPageWhenTrafficIsInsignificant() {
        IncidentTriageDecision decision = triage.decide(
                true,
                false,
                IncidentSignal.HIGH_LATENCY,
                IncidentHop.APPLICATION
        );

        assertThat(decision.recommendedRunbook()).isNull();
        assertThat(decision.significantTraffic()).isFalse();
    }

    @Test
    void requiresSequentialRunbookSteps() {
        assertThatIllegalArgumentException().isThrownBy(() -> runbookWithSteps(List.of(
                new RunbookStep(1, "Inspect the symptom."),
                new RunbookStep(3, "Mitigate the impact.")
        ))).withMessageContaining("expected 2");
    }

    @Test
    void preventsContentFromBreakingGeneratedMarkdownSections() {
        assertThatIllegalArgumentException().isThrownBy(() -> runbookWithSteps(List.of(
                new RunbookStep(1, "Inspect\n```\ninjected")
        ))).withMessageContaining("single line");
    }

    @Test
    void rendersACompleteCheckoutRunbook() {
        String markdown = new RunbookMarkdownRenderer().render(CheckoutRunbooks.redisDown());

        assertThat(markdown)
                .contains("# runbooks/redis-down.md")
                .contains("## Detection")
                .contains("## First actions")
                .contains("## PromQL")
                .contains("## Commands")
                .contains("## Done when");
    }

    @Test
    void rejectsMissingTriageInputs() {
        assertThatNullPointerException()
                .isThrownBy(() -> triage.decide(true, true, null, IncidentHop.APPLICATION));
    }

    private static Runbook runbookWithSteps(List<RunbookStep> steps) {
        return new Runbook(
                IncidentType.LATENCY_SPIKE,
                "Latency spike",
                List.of("p95 is elevated"),
                steps,
                List.of("rate(requests_total[5m])"),
                List.of("kubectl get pods"),
                List.of("p95 returned to baseline")
        );
    }
}
