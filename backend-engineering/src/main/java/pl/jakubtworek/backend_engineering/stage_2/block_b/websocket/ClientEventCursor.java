package pl.jakubtworek.backend_engineering.stage_2.block_b.websocket;

/** Client-side sequence rule: duplicates are ignored and gaps trigger replay. */
public final class ClientEventCursor {

    private long lastApplied;

    public ApplyResult apply(StreamEvent event) {
        if (event.sequence() <= lastApplied) {
            return ApplyResult.DUPLICATE;
        }
        if (event.sequence() != lastApplied + 1) {
            return ApplyResult.GAP;
        }
        lastApplied = event.sequence();
        return ApplyResult.APPLIED;
    }

    public long lastApplied() {
        return lastApplied;
    }

    public enum ApplyResult { APPLIED, DUPLICATE, GAP }
}
