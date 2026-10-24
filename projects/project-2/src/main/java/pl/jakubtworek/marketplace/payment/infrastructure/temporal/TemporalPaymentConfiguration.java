package pl.jakubtworek.marketplace.payment.infrastructure.temporal;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Opt-in: standardowy start aplikacji nie wymaga nowej infrastruktury. */
@Configuration
@ConditionalOnProperty(name = "marketplace.temporal.enabled", havingValue = "true")
public class TemporalPaymentConfiguration {
    public static final String QUEUE = "marketplace-payment-reconciliation";
    @Bean(destroyMethod = "shutdown")
    WorkflowServiceStubs temporalService(@Value("${marketplace.temporal.target:127.0.0.1:7233}") String target) {
        return WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
    }
    @Bean WorkflowClient temporalClient(WorkflowServiceStubs service) { return WorkflowClient.newInstance(service); }
    @Bean(initMethod = "start", destroyMethod = "shutdown")
    WorkerFactory paymentWorker(WorkflowClient client, PaymentLookupAdapter lookup) {
        var factory = WorkerFactory.newInstance(client);
        var worker = factory.newWorker(QUEUE);
        worker.registerWorkflowImplementationTypes(PaymentReconciliationWorkflowImpl.class);
        worker.registerActivitiesImplementations(lookup);
        return factory;
    }
}
