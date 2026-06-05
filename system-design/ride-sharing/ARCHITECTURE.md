# Architecture Notes

## MVP boundary

This project is a modular monolith. The internal modules mirror future service boundaries:

- `auth` — JWT authentication and registration
- `user` — passenger/user profile
- `driver` — driver profile, vehicle metadata and availability
- `location` — live location in Redis GEO
- `matching` — simple nearest-driver matching
- `ride` — ride lifecycle and state machine
- `maps` — route estimate abstraction, currently mock implementation
- `payment` — payment abstraction, currently mock implementation
- `websocket` — real-time ride events over STOMP

## Current simplifications

- Driver verification is automatically set to `VERIFIED` in MVP.
- Maps provider is mocked with a haversine-based estimate.
- Payment provider is mocked and always authorizes/captures successfully.
- Matching is nearest-driver by Redis GEO distance, not ML/ranking-based.
- WebSocket authentication is intentionally lightweight; HTTP remains the source of truth.

## Next extraction candidates

1. Location Service
2. Matching Service
3. Payment Service
4. Notification / Realtime Gateway
5. Pricing Service
