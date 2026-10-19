package pl.jakubtworek.backend_engineering.stage_2.block_c.progressive_delivery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Checks that a postmortem contains evidence and owned follow-up work, not only prose. */
public final class PostmortemValidator {

    public record Action(String description, String owner, Instant dueAt) {}

    public record Postmortem(String impact, String rootCause, List<String> contributingFactors,
                             List<Action> actions, IncidentTimeline timeline) {}

    public List<String> validate(Postmortem postmortem, Instant writtenAt) {
        List<String> violations = new ArrayList<>();
        if (postmortem.impact() == null || postmortem.impact().isBlank()) violations.add("impact is required");
        if (postmortem.rootCause() == null || postmortem.rootCause().isBlank()) violations.add("root cause is required");
        if (postmortem.contributingFactors() == null || postmortem.contributingFactors().isEmpty()) {
            violations.add("contributing factors are required");
        }
        if (postmortem.actions() == null || postmortem.actions().isEmpty()) {
            violations.add("follow-up actions are required");
        } else {
            for (Action action : postmortem.actions()) {
                if (action.description() == null || action.description().isBlank()
                        || action.owner() == null || action.owner().isBlank()
                        || action.dueAt() == null || !action.dueAt().isAfter(writtenAt)) {
                    violations.add("every action needs a description, owner and future due date");
                }
            }
        }
        if (postmortem.timeline() == null || !containsRequiredEvents(postmortem.timeline().events())) {
            violations.add("timeline must include detection, mitigation and recovery");
        }
        return List.copyOf(violations);
    }

    private static boolean containsRequiredEvents(List<IncidentTimeline.Event> events) {
        return events.stream().anyMatch(event -> event.type() == IncidentTimeline.EventType.DETECTED)
                && events.stream().anyMatch(event -> event.type() == IncidentTimeline.EventType.MITIGATION_STARTED)
                && events.stream().anyMatch(event -> event.type() == IncidentTimeline.EventType.RECOVERED);
    }
}
