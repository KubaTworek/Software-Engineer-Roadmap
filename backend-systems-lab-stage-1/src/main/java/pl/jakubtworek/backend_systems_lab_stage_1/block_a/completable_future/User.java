package pl.jakubtworek.backend_systems_lab_stage_1.block_a.completable_future;

record User(int id, String name) {}
record Orders(int userId, int count) {}
record Payments(int userId, boolean paid) {}
record AggregatedResponse(User user, Orders orders, Payments payments) {}