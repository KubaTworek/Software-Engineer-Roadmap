package pl.jakubtworek.marketplace.process;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Profile("postgres")
@Repository
public class JdbcOrderFulfillmentProcessRepository implements OrderFulfillmentProcessRepository {
    private final JdbcTemplate jdbc;

    public JdbcOrderFulfillmentProcessRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<OrderFulfillmentProcess> findByOrderId(UUID orderId) {
        return jdbc.query("SELECT * FROM integration.order_fulfillment_processes WHERE order_id = ?",
                (rs, row) -> OrderFulfillmentProcess.restore(
                        rs.getObject("order_id", UUID.class), rs.getTimestamp("deadline_at").toInstant(),
                        OrderFulfillmentProcess.Status.valueOf(rs.getString("status")),
                        steps(rs.getArray("completed_steps")), rs.getLong("version")), orderId)
                .stream().findFirst();
    }

    @Override
    public OrderFulfillmentProcess save(OrderFulfillmentProcess process) {
        int changed;
        if (process.version() == 0L) {
            changed = jdbc.update("""
                    INSERT INTO integration.order_fulfillment_processes
                        (order_id, status, completed_steps, deadline_at, version, updated_at)
                    VALUES (?, ?, ?::text[], ?, 1, now()) ON CONFLICT (order_id) DO NOTHING
                    """, process.orderId(), process.status().name(), stepNames(process),
                    Timestamp.from(process.deadline()));
        } else {
            changed = jdbc.update("""
                    UPDATE integration.order_fulfillment_processes
                    SET status = ?, completed_steps = ?::text[], deadline_at = ?,
                        version = version + 1, updated_at = now()
                    WHERE order_id = ? AND version = ?
                    """, process.status().name(), stepNames(process), Timestamp.from(process.deadline()),
                    process.orderId(), process.version());
        }
        if (changed != 1) throw new ConcurrentProcessModificationException(process.orderId());
        process.persistedAs(process.version() + 1);
        return process;
    }

    @Override
    public List<OrderFulfillmentProcess> findExpiredPending(int limit) {
        return jdbc.query("""
                SELECT * FROM integration.order_fulfillment_processes
                WHERE status = 'PENDING' AND deadline_at <= now()
                ORDER BY deadline_at FOR UPDATE SKIP LOCKED LIMIT ?
                """, (rs, row) -> OrderFulfillmentProcess.restore(
                rs.getObject("order_id", UUID.class), rs.getTimestamp("deadline_at").toInstant(),
                OrderFulfillmentProcess.Status.valueOf(rs.getString("status")),
                steps(rs.getArray("completed_steps")), rs.getLong("version")), limit);
    }

    private String[] stepNames(OrderFulfillmentProcess process) {
        return process.completedSteps().stream().map(Enum::name).toArray(String[]::new);
    }

    private Set<OrderFulfillmentProcess.Step> steps(Array array) {
        try {
            String[] values = (String[]) array.getArray();
            EnumSet<OrderFulfillmentProcess.Step> result = EnumSet.noneOf(OrderFulfillmentProcess.Step.class);
            Arrays.stream(values).map(OrderFulfillmentProcess.Step::valueOf).forEach(result::add);
            return result;
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Cannot restore saga steps", exception);
        }
    }
}
