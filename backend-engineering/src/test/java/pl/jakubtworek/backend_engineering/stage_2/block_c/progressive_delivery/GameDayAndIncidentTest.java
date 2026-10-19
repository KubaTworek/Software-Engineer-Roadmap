package pl.jakubtworek.backend_engineering.stage_2.block_c.progressive_delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameDayAndIncidentTest {

    @Test
    void rejectsUnboundedOrUnapprovedFaultInjection() {
        GameDayPlan unsafe = new GameDayPlan(
                "cache loss should degrade reads", "production", 100,
                Duration.ofHours(1), "", "", false);

        assertThat(unsafe.validate(5, Duration.ofMinutes(10))).contains(
                "blast radius exceeds limit",
                "duration exceeds limit",
                "abort condition is required",
                "recovery action is required",
                "explicit approval is required");
    }

    @Test
    void boundedInjectorFailsOnlyThePlannedNumberOfCalls() {
        FaultInjector injector = new FaultInjector("redis-timeout", 2);

        assertThatThrownBy(() -> injector.execute(() -> "ok"))
                .isInstanceOf(FaultInjector.InjectedFaultException.class);
        assertThatThrownBy(() -> injector.execute(() -> "ok"))
                .isInstanceOf(FaultInjector.InjectedFaultException.class);
        assertThat(injector.execute(() -> "ok")).isEqualTo("ok");
    }

    @Test
    void scenarioTestVerifiesRunbookTimelineAndPostmortem() {
        IncidentRunbook runbook = new IncidentRunbook();
        IncidentRunbook.Result result = runbook.execute(
                List.of(IncidentRunbook.Step.values()),
                new IncidentRunbook.Scenario(true, true, true));

        Instant detectedAt = Instant.parse("2026-01-01T10:00:00Z");
        IncidentTimeline timeline = new IncidentTimeline();
        timeline.append(detectedAt, IncidentTimeline.EventType.DETECTED, "canary error rate crossed 2%");
        timeline.append(detectedAt.plusSeconds(30), IncidentTimeline.EventType.DECLARED, "incident INC-42 declared");
        timeline.append(detectedAt.plusSeconds(60), IncidentTimeline.EventType.MITIGATION_STARTED, "rollback and kill switch");
        timeline.append(detectedAt.plusSeconds(90), IncidentTimeline.EventType.CHANGE_APPLIED, "stable revision receives 100% traffic");
        timeline.append(detectedAt.plusSeconds(150), IncidentTimeline.EventType.RECOVERED, "error rate and p99 returned to baseline");

        PostmortemValidator.Postmortem postmortem = new PostmortemValidator.Postmortem(
                "4% checkout requests failed for 150 seconds",
                "candidate opened an unbounded downstream connection pool",
                List.of("canary window was too long"),
                List.of(new PostmortemValidator.Action(
                        "add pool saturation rollback signal", "payments-team", detectedAt.plus(Duration.ofDays(14)))),
                timeline);

        assertThat(result.recovered()).isTrue();
        assertThat(result.rolloutStopped()).isTrue();
        assertThat(result.killSwitchActive()).isTrue();
        assertThat(result.evidenceCaptured()).isTrue();
        assertThat(timeline.timeToRecovery()).isEqualTo(Duration.ofSeconds(150));
        assertThat(new PostmortemValidator().validate(postmortem, detectedAt.plusSeconds(300))).isEmpty();
    }

    @Test
    void runbookFailsWhenMitigationIsMissingOrInWrongOrder() {
        IncidentRunbook runbook = new IncidentRunbook();

        assertThatThrownBy(() -> runbook.execute(
                        List.of(
                                IncidentRunbook.Step.DECLARE_INCIDENT,
                                IncidentRunbook.Step.ACTIVATE_KILL_SWITCH,
                                IncidentRunbook.Step.STOP_ROLLOUT,
                                IncidentRunbook.Step.CAPTURE_EVIDENCE,
                                IncidentRunbook.Step.VERIFY_RECOVERY),
                        new IncidentRunbook.Scenario(true, true, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe order");
    }
}
