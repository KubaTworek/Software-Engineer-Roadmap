package pl.jakubtworek.backend_engineering.stage_2.block_b.websocket;

import java.util.ArrayList;
import java.util.List;

/** Deliberate counterexample: a disconnected or slow client grows memory without a limit. */
public final class NaiveUnboundedSession {

    private final List<StreamEvent> pending = new ArrayList<>();

    public void offer(StreamEvent event) {
        pending.add(event);
    }

    public int pendingCount() {
        return pending.size();
    }
}
