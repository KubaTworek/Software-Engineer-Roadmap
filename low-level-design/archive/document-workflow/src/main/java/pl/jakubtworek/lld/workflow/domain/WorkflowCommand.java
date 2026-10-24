package pl.jakubtworek.lld.workflow.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Intencje jako dane: ten sam obiekt polecenia można ocenić i wykonać. Konstrukcja
 * nie gwarantuje uprawnień ani aktualności wersji. Puste uzasadnienie zostawiamy
 * walidacji domenowej, aby raport mógł podać REASON_REQUIRED bez rzucania wyjątku.
 */
public sealed interface WorkflowCommand {

    enum Operation {

        EDIT, SUBMIT, APPROVE, REJECT, WITHDRAW
    }

    Operation operation();

    default Optional<ReviewToken> token() {
        return Optional.empty();
    }

    record Edit(DocumentContent content) implements WorkflowCommand {

        public Edit {
            Objects.requireNonNull(content);
        }

        @Override
        public Operation operation() {
            return Operation.EDIT;
        }
    }

    record Submit() implements WorkflowCommand {

        @Override
        public Operation operation() {
            return Operation.SUBMIT;
        }
    }

    record Approve(ReviewToken reviewToken) implements WorkflowCommand {

        public Approve {
            Objects.requireNonNull(reviewToken);
        }

        @Override
        public Operation operation() {
            return Operation.APPROVE;
        }

        @Override
        public Optional<ReviewToken> token() {
            return Optional.of(reviewToken);
        }
    }

    record Reject(ReviewToken reviewToken, String reason) implements WorkflowCommand {

        public Reject {
            Objects.requireNonNull(reviewToken);
        }

        @Override
        public Operation operation() {
            return Operation.REJECT;
        }

        @Override
        public Optional<ReviewToken> token() {
            return Optional.of(reviewToken);
        }
    }

    record Withdraw(String reason) implements WorkflowCommand {

        @Override
        public Operation operation() {
            return Operation.WITHDRAW;
        }
    }
}
