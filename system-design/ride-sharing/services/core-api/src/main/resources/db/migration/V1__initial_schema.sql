CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    phone_number VARCHAR(40) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    rating NUMERIC(3,2) NOT NULL DEFAULT 5.00,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE drivers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES app_users(id),
    verification_status VARCHAR(40) NOT NULL,
    availability_status VARCHAR(40) NOT NULL,
    vehicle_make VARCHAR(100),
    vehicle_model VARCHAR(100),
    plate_number VARCHAR(40),
    vehicle_color VARCHAR(80),
    vehicle_type VARCHAR(40),
    rating NUMERIC(3,2) NOT NULL DEFAULT 5.00,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE rides (
    id UUID PRIMARY KEY,
    passenger_id UUID NOT NULL REFERENCES app_users(id),
    driver_id UUID REFERENCES drivers(id),
    status VARCHAR(50) NOT NULL,
    pickup_lat NUMERIC(10,7) NOT NULL,
    pickup_lng NUMERIC(10,7) NOT NULL,
    pickup_address VARCHAR(500),
    dropoff_lat NUMERIC(10,7) NOT NULL,
    dropoff_lng NUMERIC(10,7) NOT NULL,
    dropoff_address VARCHAR(500),
    estimated_distance_km NUMERIC(8,2),
    estimated_duration_minutes INTEGER,
    estimated_price NUMERIC(10,2),
    final_price NUMERIC(10,2),
    currency VARCHAR(3) NOT NULL DEFAULT 'PLN',
    requested_at TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP,
    driver_arrived_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancellation_reason VARCHAR(500),
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    ride_id UUID NOT NULL UNIQUE REFERENCES rides(id),
    passenger_id UUID NOT NULL REFERENCES app_users(id),
    amount NUMERIC(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_payment_id VARCHAR(255),
    idempotency_key VARCHAR(255) UNIQUE,
    authorized_at TIMESTAMP,
    captured_at TIMESTAMP,
    failed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_rides_passenger ON rides(passenger_id);
CREATE INDEX idx_rides_driver ON rides(driver_id);
CREATE INDEX idx_rides_status ON rides(status);
CREATE INDEX idx_drivers_availability ON drivers(availability_status);
