package pl.jakubtworek.booking.service.pitfall;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SelfInvocationPitfallService {
    private final ObjectProvider<SelfInvocationPitfallService> selfProvider;

    public SelfInvocationPitfallService(ObjectProvider<SelfInvocationPitfallService> selfProvider) {
        this.selfProvider = selfProvider;
    }

    public boolean callTransactionalMethodThroughThis() {
        return transactionalMethod();
    }

    public boolean callTransactionalMethodThroughProxy() {
        return selfProvider.getObject().transactionalMethod();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean transactionalMethod() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }
}
