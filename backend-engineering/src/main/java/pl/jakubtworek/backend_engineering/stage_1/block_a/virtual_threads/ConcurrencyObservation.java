package pl.jakubtworek.backend_engineering.stage_1.block_a.virtual_threads;

public record ConcurrencyObservation(
        int submittedTasks,
        int startedWhileBlocked,
        int maximumActiveTasks,
        boolean everyStartedTaskWasVirtual
) {
}
