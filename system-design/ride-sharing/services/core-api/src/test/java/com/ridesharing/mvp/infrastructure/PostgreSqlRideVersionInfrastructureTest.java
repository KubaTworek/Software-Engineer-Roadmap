package com.ridesharing.mvp.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pokazuje wykonywalną granicę optimistic locking. Dwa workery mogą przeczytać
 * wersję 0, lecz tylko pierwszy zapis z warunkiem version=0 zmienia przejazd.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlRideVersionInfrastructureTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void staleWorkerCannotOverwriteACommittedRideTransition() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        UUID passengerId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();
        try (Connection connection = POSTGRES.createConnection("")) {
            insertPassenger(connection, passengerId);
            insertRide(connection, rideId, passengerId);

            assertThat(transition(connection, rideId, "MATCHING", 0)).isEqualTo(1);
            assertThat(transition(connection, rideId, "CANCELLED_BY_PASSENGER", 0)).isZero();
        }
    }

    @Test
    void databaseRejectsTwoActiveRidesForOneDriver() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = POSTGRES.createConnection("")) {
            UUID passengerId = UUID.randomUUID();
            UUID driverUserId = UUID.randomUUID();
            UUID driverId = UUID.randomUUID();
            insertPassenger(connection, passengerId);
            insertPassenger(connection, driverUserId);
            insertDriver(connection, driverId, driverUserId);
            insertAssignedRide(connection, UUID.randomUUID(), passengerId, driverId);

            assertThatThrownBy(() -> insertAssignedRide(
                    connection, UUID.randomUUID(), passengerId, driverId
            )).hasMessageContaining("uq_rides_active_driver");
        }
    }

    private void insertPassenger(Connection connection, UUID passengerId) throws Exception {
        try (var statement = connection.prepareStatement("""
                INSERT INTO app_users(id, email, phone_number, password_hash, full_name, role,
                                      status, rating, created_at, updated_at)
                VALUES (?, ?, ?, 'hash', 'Passenger', 'PASSENGER', 'ACTIVE', 5.0, ?, ?)
                """)) {
            statement.setObject(1, passengerId);
            statement.setString(2, passengerId + "@example.test");
            statement.setString(3, passengerId.toString().substring(0, 20));
            statement.setObject(4, LocalDateTime.now());
            statement.setObject(5, LocalDateTime.now());
            statement.executeUpdate();
        }
    }

    private void insertRide(Connection connection, UUID rideId, UUID passengerId) throws Exception {
        try (var statement = connection.prepareStatement("""
                INSERT INTO rides(
                    id, passenger_id, status, pickup_lat, pickup_lng, dropoff_lat, dropoff_lng,
                    estimated_price, currency, requested_at, version, created_at, updated_at
                ) VALUES (?, ?, 'REQUESTED', 52.23, 21.01, 52.24, 21.02,
                          ?, 'PLN', ?, 0, ?, ?)
                """)) {
            statement.setObject(1, rideId);
            statement.setObject(2, passengerId);
            statement.setBigDecimal(3, BigDecimal.valueOf(25));
            statement.setObject(4, LocalDateTime.now());
            statement.setObject(5, LocalDateTime.now());
            statement.setObject(6, LocalDateTime.now());
            statement.executeUpdate();
        }
    }

    private int transition(Connection connection, UUID rideId, String status, int expectedVersion) throws Exception {
        try (var statement = connection.prepareStatement("""
                UPDATE rides SET status = ?, version = version + 1, updated_at = ?
                WHERE id = ? AND version = ?
                """)) {
            statement.setString(1, status);
            statement.setObject(2, LocalDateTime.now());
            statement.setObject(3, rideId);
            statement.setInt(4, expectedVersion);
            return statement.executeUpdate();
        }
    }

    private void insertDriver(Connection connection, UUID driverId, UUID userId) throws Exception {
        try (var statement = connection.prepareStatement("""
                INSERT INTO drivers(id, user_id, verification_status, availability_status,
                                    rating, created_at, updated_at)
                VALUES (?, ?, 'VERIFIED', 'ON_TRIP', 5.0, ?, ?)
                """)) {
            statement.setObject(1, driverId);
            statement.setObject(2, userId);
            statement.setObject(3, LocalDateTime.now());
            statement.setObject(4, LocalDateTime.now());
            statement.executeUpdate();
        }
    }

    private void insertAssignedRide(
            Connection connection,
            UUID rideId,
            UUID passengerId,
            UUID driverId
    ) throws Exception {
        try (var statement = connection.prepareStatement("""
                INSERT INTO rides(
                    id, passenger_id, driver_id, status, pickup_lat, pickup_lng,
                    dropoff_lat, dropoff_lng, estimated_price, currency,
                    requested_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, 'DRIVER_ASSIGNED', 52.23, 21.01, 52.24, 21.02,
                          25.00, 'PLN', ?, 0, ?, ?)
                """)) {
            statement.setObject(1, rideId);
            statement.setObject(2, passengerId);
            statement.setObject(3, driverId);
            statement.setObject(4, LocalDateTime.now());
            statement.setObject(5, LocalDateTime.now());
            statement.setObject(6, LocalDateTime.now());
            statement.executeUpdate();
        }
    }
}
