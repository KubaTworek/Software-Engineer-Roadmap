package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.deduplication;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * PostgreSQL implementation of processed event tracking.
 *
 * This implementation assumes a table similar to:
 *
 * CREATE TABLE processed_events (
 *     event_id UUID PRIMARY KEY,
 *     processed_at TIMESTAMP NOT NULL DEFAULT now()
 * );
 *
 * The INSERT ... ON CONFLICT DO NOTHING pattern makes deduplication atomic.
 */
public class PostgresProcessedEventRepository implements ProcessedEventRepository {

    private final DataSource dataSource;

    public PostgresProcessedEventRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Attempts to insert the event ID into processed_events.
     *
     * If the row is inserted, the event is new.
     * If no row is inserted, the event has already been processed.
     */
    @Override
    public boolean tryMarkAsProcessed(UUID eventId) {
        String sql = """
                INSERT INTO processed_events(event_id, processed_at)
                VALUES (?, now())
                ON CONFLICT (event_id) DO NOTHING
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(1, eventId);

            int insertedRows = statement.executeUpdate();

            return insertedRows == 1;
        } catch (SQLException exception) {
            throw new DeduplicationException("Could not mark event as processed.", exception);
        }
    }

    /**
     * Removes a processed event marker.
     *
     * This can be used when deduplication and business operations are not wrapped
     * in the same database transaction.
     */
    @Override
    public void removeProcessedMarker(UUID eventId) {
        String sql = "DELETE FROM processed_events WHERE event_id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(1, eventId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DeduplicationException("Could not remove processed event marker.", exception);
        }
    }
}