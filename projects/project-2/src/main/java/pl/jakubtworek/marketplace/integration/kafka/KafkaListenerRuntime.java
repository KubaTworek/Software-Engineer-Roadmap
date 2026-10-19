package pl.jakubtworek.marketplace.integration.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.fulfillment.application.StartFulfillmentOnOrderConfirmedHandler;
import pl.jakubtworek.marketplace.integration.kafka.infrastructure.SpringConsumerUnitOfWork;
import pl.jakubtworek.marketplace.inventory.application.ReserveStockOnOrderPlacedHandler;
import pl.jakubtworek.marketplace.inventory.application.ReleaseStockHandler;
import pl.jakubtworek.marketplace.notification.application.NotifyOnOrderConfirmedHandler;
import pl.jakubtworek.marketplace.ordering.application.*;
import pl.jakubtworek.marketplace.payment.application.ReservePaymentOnOrderPlacedHandler;
import pl.jakubtworek.marketplace.payment.application.ReleasePaymentHandler;
import pl.jakubtworek.marketplace.process.*;
import pl.jakubtworek.marketplace.shared.events.ApplicationEventBus;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;
import pl.jakubtworek.marketplace.shared.observability.ObservabilityService;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Produkcyjna granica odbioru. Każdy bounded context ma własną consumer group i
 * zawężony dispatcher. Dzięki temu Payment nie uruchamia przypadkiem Inventory,
 * mimo że oba reagują na ten sam opublikowany fakt {@code OrderPlaced}.
 *
 * Manual acknowledgement następuje dopiero po transakcji efekt + outbox + marker.
 * Redelivery po awarii między transakcją a ACK jest bezpieczne dzięki
 * {@code processed_events}; nie jest to deklaracja exactly-once całego systemu.
 */
@Profile("kafka")
@Component
@ConditionalOnProperty(name = "marketplace.kafka.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaListenerRuntime {
    private final KafkaConsumerWorker payment;
    private final KafkaConsumerWorker inventory;
    private final KafkaConsumerWorker paymentCompensation;
    private final KafkaConsumerWorker inventoryCompensation;
    private final KafkaConsumerWorker orderingFromPayment;
    private final KafkaConsumerWorker orderingFromInventory;
    private final KafkaConsumerWorker processFromOrder;
    private final KafkaConsumerWorker processFromPayment;
    private final KafkaConsumerWorker processFromInventory;
    private final KafkaConsumerWorker fulfillment;
    private final KafkaConsumerWorker notification;

    public KafkaListenerRuntime(
            KafkaMessageBroker broker,
            ProcessedEventRepository processed,
            DlqEventRepository dlq,
            ObjectMapper mapper,
            ObservabilityService observability,
            SpringConsumerUnitOfWork unitOfWork,
            KafkaTopicResolver topics,
            ReservePaymentOnOrderPlacedHandler reservePayment,
            ReleasePaymentHandler releasePayment,
            ReserveStockOnOrderPlacedHandler reserveStock,
            ReleaseStockHandler releaseStock,
            ConfirmPaymentOnPaymentReservedHandler confirmPayment,
            RejectOrderOnPaymentRejectedHandler rejectPayment,
            ConfirmStockOnStockReservedHandler confirmStock,
            RejectOrderOnStockReservationFailedHandler rejectStock,
            StartOrderFulfillmentProcessHandler startProcess,
            RecordPaymentProcessFactHandler processPayment,
            RecordPaymentRejectionProcessFactHandler processPaymentRejection,
            RecordPaymentReleasedProcessFactHandler processPaymentReleased,
            RecordStockProcessFactHandler processStock,
            RecordStockRejectionProcessFactHandler processStockRejection,
            RecordStockReleasedProcessFactHandler processStockReleased,
            StartFulfillmentOnOrderConfirmedHandler startFulfillment,
            NotifyOnOrderConfirmedHandler notify,
            @Value("${marketplace.kafka.consumer-groups.payment}") String paymentGroup,
            @Value("${marketplace.kafka.consumer-groups.inventory}") String inventoryGroup,
            @Value("${marketplace.kafka.consumer-groups.ordering}") String orderingGroup,
            @Value("${marketplace.kafka.consumer-groups.process}") String processGroup,
            @Value("${marketplace.kafka.consumer-groups.fulfillment}") String fulfillmentGroup,
            @Value("${marketplace.kafka.consumer-groups.notification}") String notificationGroup
    ) {
        payment = worker("payment", topics.resolve("OrderPlaced").topicName(), paymentGroup,
                broker, processed, dlq, mapper, observability, unitOfWork, reservePayment);
        inventory = worker("inventory", topics.resolve("OrderPlaced").topicName(), inventoryGroup,
                broker, processed, dlq, mapper, observability, unitOfWork, reserveStock);
        paymentCompensation = worker("payment-compensation", topics.resolve("PaymentReleaseRequested").topicName(), paymentGroup,
                broker, processed, dlq, mapper, observability, unitOfWork, releasePayment);
        inventoryCompensation = worker("inventory-compensation", topics.resolve("StockReleaseRequested").topicName(), inventoryGroup,
                broker, processed, dlq, mapper, observability, unitOfWork, releaseStock);
        orderingFromPayment = worker("ordering-payment", topics.resolve("PaymentReserved").topicName(), orderingGroup,
                broker, processed, dlq, mapper, observability, unitOfWork, confirmPayment, rejectPayment);
        orderingFromInventory = worker("ordering-inventory", topics.resolve("StockReserved").topicName(), orderingGroup,
                broker, processed, dlq, mapper, observability, unitOfWork, confirmStock, rejectStock);
        processFromOrder = worker("process-order", topics.resolve("OrderPlaced").topicName(), processGroup,
                broker, processed, dlq, mapper, observability, unitOfWork, startProcess);
        processFromPayment = worker("process-payment", topics.resolve("PaymentReserved").topicName(), processGroup,
                broker, processed, dlq, mapper, observability, unitOfWork,
                processPayment, processPaymentRejection, processPaymentReleased);
        processFromInventory = worker("process-inventory", topics.resolve("StockReserved").topicName(), processGroup,
                broker, processed, dlq, mapper, observability, unitOfWork,
                processStock, processStockRejection, processStockReleased);
        fulfillment = worker("fulfillment", topics.resolve("OrderConfirmed").topicName(), fulfillmentGroup,
                broker, processed, dlq, mapper, observability, unitOfWork, startFulfillment);
        notification = worker("notification", topics.resolve("OrderConfirmed").topicName(), notificationGroup,
                broker, processed, dlq, mapper, observability, unitOfWork, notify);
    }

    private KafkaConsumerWorker worker(String name, String topic, String group, KafkaMessageBroker broker,
            ProcessedEventRepository processed, DlqEventRepository dlq, ObjectMapper mapper,
            ObservabilityService observability, SpringConsumerUnitOfWork unitOfWork,
            DomainEventHandler<?>... handlers) {
        return new KafkaConsumerWorker(name, topic, group, broker,
                new ApplicationEventBus(List.of(handlers)), processed, dlq, mapper,
                RetryPolicy.defaultPolicy(), observability, unitOfWork);
    }

    @KafkaListener(topics = "${marketplace.kafka.topics.order-events}", groupId = "${marketplace.kafka.consumer-groups.payment}")
    void payment(ConsumerRecord<String, IntegrationEventEnvelope> record, Acknowledgment ack) { process(payment, record, ack); }

    @KafkaListener(topics = "${marketplace.kafka.topics.order-events}", groupId = "${marketplace.kafka.consumer-groups.inventory}")
    void inventory(ConsumerRecord<String, IntegrationEventEnvelope> record, Acknowledgment ack) { process(inventory, record, ack); }

    @KafkaListener(topics = "${marketplace.kafka.topics.payment-events}", groupId = "${marketplace.kafka.consumer-groups.payment}")
    void paymentCompensation(ConsumerRecord<String, IntegrationEventEnvelope> record, Acknowledgment ack) { process(paymentCompensation, record, ack); }

    @KafkaListener(topics = "${marketplace.kafka.topics.inventory-events}", groupId = "${marketplace.kafka.consumer-groups.inventory}")
    void inventoryCompensation(ConsumerRecord<String, IntegrationEventEnvelope> record, Acknowledgment ack) { process(inventoryCompensation, record, ack); }

    @KafkaListener(topics = "${marketplace.kafka.topics.payment-events}", groupId = "${marketplace.kafka.consumer-groups.ordering}")
    void orderingPayment(ConsumerRecord<String, IntegrationEventEnvelope> record, Acknowledgment ack) { process(orderingFromPayment, record, ack); }

    @KafkaListener(topics = "${marketplace.kafka.topics.inventory-events}", groupId = "${marketplace.kafka.consumer-groups.ordering}")
    void orderingInventory(ConsumerRecord<String, IntegrationEventEnvelope> record, Acknowledgment ack) { process(orderingFromInventory, record, ack); }

    @KafkaListener(topics = "${marketplace.kafka.topics.order-events}", groupId = "${marketplace.kafka.consumer-groups.process}")
    void processOrder(ConsumerRecord<String, IntegrationEventEnvelope> record, Acknowledgment ack) { process(processFromOrder, record, ack); }

    @KafkaListener(topics = "${marketplace.kafka.topics.payment-events}", groupId = "${marketplace.kafka.consumer-groups.process}")
    void processPayment(ConsumerRecord<String, IntegrationEventEnvelope> record, Acknowledgment ack) { process(processFromPayment, record, ack); }

    @KafkaListener(topics = "${marketplace.kafka.topics.inventory-events}", groupId = "${marketplace.kafka.consumer-groups.process}")
    void processInventory(ConsumerRecord<String, IntegrationEventEnvelope> record, Acknowledgment ack) { process(processFromInventory, record, ack); }

    @KafkaListener(topics = "${marketplace.kafka.topics.order-events}", groupId = "${marketplace.kafka.consumer-groups.fulfillment}")
    void fulfillment(ConsumerRecord<String, IntegrationEventEnvelope> record, Acknowledgment ack) { process(fulfillment, record, ack); }

    @KafkaListener(topics = "${marketplace.kafka.topics.order-events}", groupId = "${marketplace.kafka.consumer-groups.notification}")
    void notification(ConsumerRecord<String, IntegrationEventEnvelope> record, Acknowledgment ack) { process(notification, record, ack); }

    private void process(KafkaConsumerWorker worker, ConsumerRecord<String, IntegrationEventEnvelope> source, Acknowledgment ack) {
        KafkaRecord record = new KafkaRecord(source.topic(), source.partition(), source.key(), source.value(), source.offset());
        worker.processRecord(record, ack::acknowledge);
    }
}
