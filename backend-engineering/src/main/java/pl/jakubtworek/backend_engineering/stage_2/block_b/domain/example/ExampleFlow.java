package pl.jakubtworek.backend_engineering.stage_2.block_b.domain.example;

import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.order.OrderPlaced;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.payment.PaymentAuthorized;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.shipping.ShippingInitiated;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.orders.Order;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.orders.OrderEventFactory;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.orders.OrderItem;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.payments.Payment;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.payments.PaymentEventFactory;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.shipping.Shipment;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.shipping.ShippingEventFactory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Simple example showing how events can be created during an order workflow.
 *
 * In a real system, these events would usually be saved to an outbox table
 * and then published asynchronously to Kafka.
 */
public class ExampleFlow {

    public static void main(String[] args) {
        Order order = new Order(
                "ORD-12345",
                List.of(
                        new OrderItem("P-001", 2, new BigDecimal("49.99")),
                        new OrderItem("P-010", 1, new BigDecimal("60.01"))
                ),
                new BigDecimal("159.99")
        );

        OrderEventFactory orderEventFactory = new OrderEventFactory();
        OrderPlaced orderPlaced = orderEventFactory.orderPlaced(order);

        Payment payment = new Payment(
                "PAY-98765",
                order.orderId(),
                order.totalAmount()
        );

        payment.authorize();

        PaymentEventFactory paymentEventFactory = new PaymentEventFactory();
        PaymentAuthorized paymentAuthorized =
                paymentEventFactory.paymentAuthorized(payment, orderPlaced);

        Shipment shipment = new Shipment(
                "SHIP-55555",
                order.orderId()
        );

        ShippingEventFactory shippingEventFactory = new ShippingEventFactory();
        ShippingInitiated shippingInitiated =
                shippingEventFactory.shippingInitiated(shipment, paymentAuthorized);

        System.out.println(orderPlaced);
        System.out.println(paymentAuthorized);
        System.out.println(shippingInitiated);
    }
}