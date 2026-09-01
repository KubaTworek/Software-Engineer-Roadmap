package pl.jakubtworek.backend_engineering.stage_2.block_a.reference_flow.application;

/** Outbox relay publishes live updates only after a newer projection version was accepted. */
public final class CatalogProjectionRelay {

    private final ProductSearchProjection searchProjection;
    private final LiveProductUpdates liveUpdates;

    public CatalogProjectionRelay(ProductSearchProjection searchProjection, LiveProductUpdates liveUpdates) {
        this.searchProjection = searchProjection;
        this.liveUpdates = liveUpdates;
    }

    public boolean relay(ProductChangedMessage message) {
        if (!searchProjection.apply(message)) {
            return false;
        }
        liveUpdates.publish("PRODUCT_CHANGED:" + message.productId() + ":" + message.version());
        return true;
    }
}
