package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.runbook;

import java.util.List;
import java.util.Objects;

/**
 * Represents an operational runbook.
 *
 * A runbook should support mitigation-first incident response.
 * Root cause analysis can happen later; the first job is to reduce user impact.
 */
public final class Runbook {

    private final IncidentType incidentType;
    private final String title;
    private final List<String> detectionSignals;
    private final List<RunbookStep> firstActions;
    private final List<String> promqlQueries;
    private final List<String> commands;
    private final List<String> doneWhen;

    public Runbook(
            IncidentType incidentType,
            String title,
            List<String> detectionSignals,
            List<RunbookStep> firstActions,
            List<String> promqlQueries,
            List<String> commands,
            List<String> doneWhen
    ) {
        this.incidentType = Objects.requireNonNull(incidentType, "incidentType must not be null");
        this.title = requireNonBlank(title, "title");
        this.detectionSignals = copyNonEmpty(detectionSignals, "detectionSignals");
        this.firstActions = List.copyOf(Objects.requireNonNull(firstActions, "firstActions must not be null"));
        this.promqlQueries = copyNonEmpty(promqlQueries, "promqlQueries");
        this.commands = copyNonEmpty(commands, "commands");
        this.doneWhen = copyNonEmpty(doneWhen, "doneWhen");

        if (this.firstActions.isEmpty()) {
            throw new IllegalArgumentException("runbook must contain at least one first action");
        }
    }

    public IncidentType incidentType() {
        return incidentType;
    }

    public String title() {
        return title;
    }

    public List<String> detectionSignals() {
        return detectionSignals;
    }

    public List<RunbookStep> firstActions() {
        return firstActions;
    }

    public List<String> promqlQueries() {
        return promqlQueries;
    }

    public List<String> commands() {
        return commands;
    }

    public List<String> doneWhen() {
        return doneWhen;
    }

    private static List<String> copyNonEmpty(List<String> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + " must not be null");

        if (values.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }

        for (String value : values) {
            requireNonBlank(value, fieldName + " item");
        }

        return List.copyOf(values);
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}