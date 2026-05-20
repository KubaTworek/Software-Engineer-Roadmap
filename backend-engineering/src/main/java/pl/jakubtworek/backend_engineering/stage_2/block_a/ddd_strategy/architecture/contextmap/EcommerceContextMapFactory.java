package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.architecture.contextmap;

import java.util.List;

// Example factory building the e-commerce context map.
// This class is documentation-oriented and may be used in architecture tests.
public final class EcommerceContextMapFactory {

    public static ContextMap create() {
        BoundedContext sales = new BoundedContext(
                "sales",
                ContextType.CORE,
                "SalesTeam",
                "sql-sales",
                List.of("order-service", "catalog-service", "cart-service")
        );

        BoundedContext fulfillment = new BoundedContext(
                "fulfillment",
                ContextType.CORE,
                "LogisticsTeam",
                "sql-fulfillment",
                List.of("shipping-service", "warehouse-service")
        );

        BoundedContext billing = new BoundedContext(
                "billing",
                ContextType.SUPPORTING,
                "FinanceTeam",
                "sql-billing",
                List.of("payment-service", "invoice-service")
        );

        CommunicationChannel orderEvents = new CommunicationChannel(
                "order-events",
                ChannelType.INTEGRATION_EVENT
        );

        CommunicationChannel paymentEvents = new CommunicationChannel(
                "payment-events",
                ChannelType.INTEGRATION_EVENT
        );

        return new ContextMap(
                List.of(sales, fulfillment, billing),
                List.of(
                        new ContextRelationship(
                                sales,
                                fulfillment,
                                RelationshipPattern.PUBLISHED_LANGUAGE,
                                orderEvents
                        ),
                        new ContextRelationship(
                                sales,
                                billing,
                                RelationshipPattern.PUBLISHED_LANGUAGE,
                                orderEvents
                        ),
                        new ContextRelationship(
                                billing,
                                sales,
                                RelationshipPattern.CONFORMIST,
                                paymentEvents
                        )
                )
        );
    }

    private EcommerceContextMapFactory() {
    }
}