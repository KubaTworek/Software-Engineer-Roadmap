package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.correctness;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Reproducible ambiguous failures: the effect commits, but the first response is lost. */
public record FailurePlan(Set<String> timeoutAfterFirstEffect) {

    public FailurePlan {
        timeoutAfterFirstEffect = Set.copyOf(timeoutAfterFirstEffect);
    }

    public static FailurePlan random(List<IncrementCommand> commands, long seed, double probability) {
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("probability must be between 0 and 1");
        }
        Random random = new Random(seed);
        Set<String> failures = new LinkedHashSet<>();
        for (IncrementCommand command : commands) {
            if (random.nextDouble() < probability) {
                failures.add(command.commandId());
            }
        }
        return new FailurePlan(failures);
    }

    boolean timesOut(String commandId, int attempt) {
        return attempt == 1 && timeoutAfterFirstEffect.contains(commandId);
    }
}
