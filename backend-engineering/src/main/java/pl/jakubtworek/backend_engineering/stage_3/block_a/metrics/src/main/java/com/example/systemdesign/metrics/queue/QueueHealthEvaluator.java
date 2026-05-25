package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.queue;

import java.time.Duration;

/**
 * Evaluates queue health against business processing budget.
 */
public class QueueHealthEvaluator {

    private final Duration businessProcessingBudget;

    public QueueHealthEvaluator(Duration businessProcessingBudget) {
        if (businessProcessingBudget == null || businessProcessingBudget.isNegative() || businessProcessingBudget.isZero()) {
            throw new IllegalArgumentException("businessProcessingBudget must be positive");
        }

        this.businessProcessingBudget = businessProcessingBudget;
    }

    public boolean oldestMessageExceedsBudget(QueueMetrics metrics) {
        return metrics.oldestMessageAge().compareTo(businessProcessingBudget) > 0;
    }

    public boolean hasPersistentDlqProblem(QueueMetrics metrics) {
        return metrics.dlqMessages() > 0;
    }

    public boolean backlogGrowingWithoutHealthyWorkers(QueueMetrics metrics) {
        return metrics.queueDepth() > 0 && metrics.workerFailureRate() > 0.05;
    }
}