package com.example.ecommerce.config.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ThreadLocalRandom;

public class ReadReplicaRoutingDataSource extends AbstractRoutingDataSource {
    private final int replicaCount;

    public ReadReplicaRoutingDataSource(int replicaCount) {
        this.replicaCount = replicaCount;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly() && replicaCount > 0) {
            return "replica-" + ThreadLocalRandom.current().nextInt(replicaCount);
        }

        return "writer";
    }
}
