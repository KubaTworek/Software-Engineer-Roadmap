package pl.jakubtworek.backend_engineering.stage_2.block_c.progressive_delivery;

import java.util.EnumSet;
import java.util.List;

/** Executable runbook model used to verify order, recovery and evidence capture. */
public final class IncidentRunbook {

    public enum Step {
        DECLARE_INCIDENT,
        STOP_ROLLOUT,
        ACTIVATE_KILL_SWITCH,
        CAPTURE_EVIDENCE,
        VERIFY_RECOVERY
    }

    public record Scenario(boolean rolloutActive, boolean featureContributesToFailure,
                           boolean serviceRecoversAfterMitigation) {}

    public record Result(boolean recovered, boolean rolloutStopped,
                         boolean killSwitchActive, boolean evidenceCaptured) {}

    public Result execute(List<Step> steps, Scenario scenario) {
        requireCompleteOrderedRunbook(steps);
        boolean rolloutStopped = !scenario.rolloutActive();
        boolean killSwitchActive = false;
        boolean evidenceCaptured = false;
        boolean declared = false;

        for (Step step : steps) {
            switch (step) {
                case DECLARE_INCIDENT -> declared = true;
                case STOP_ROLLOUT -> rolloutStopped = true;
                case ACTIVATE_KILL_SWITCH -> killSwitchActive = true;
                case CAPTURE_EVIDENCE -> evidenceCaptured = true;
                case VERIFY_RECOVERY -> {
                    boolean mitigated = rolloutStopped
                            && (!scenario.featureContributesToFailure() || killSwitchActive);
                    if (!declared || !mitigated || !scenario.serviceRecoversAfterMitigation()) {
                        throw new IllegalStateException("recovery criteria are not satisfied");
                    }
                }
            }
        }
        return new Result(true, rolloutStopped, killSwitchActive, evidenceCaptured);
    }

    private static void requireCompleteOrderedRunbook(List<Step> steps) {
        if (steps == null || EnumSet.copyOf(steps).size() != Step.values().length) {
            throw new IllegalArgumentException("runbook must contain every required step exactly once");
        }
        for (int index = 0; index < Step.values().length; index++) {
            if (steps.get(index) != Step.values()[index]) {
                throw new IllegalArgumentException("runbook steps are in an unsafe order");
            }
        }
    }
}
