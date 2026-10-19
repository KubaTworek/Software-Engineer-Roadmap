package pl.jakubtworek.marketplace.integration.kafka.infrastructure;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import pl.jakubtworek.marketplace.integration.kafka.ConsumerUnitOfWork;

import java.util.function.Supplier;

/** Produkcyjna granica transakcji konsumenta oparta na lokalnej bazie danych. */
@Component
public class SpringConsumerUnitOfWork implements ConsumerUnitOfWork {

    private final TransactionTemplate transactionTemplate;

    public SpringConsumerUnitOfWork(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
