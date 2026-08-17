-- Password: admin1234
INSERT INTO users (id, email, password_hash, role, status, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin@example.com',
    '$2a$10$qlLHmoLS2K7V/64jjwHvU.7.3CTyM2vOhfs//Xzv8L5SgkGpyB3M.',
    'ADMIN',
    'ACTIVE',
    now(),
    now()
)
ON CONFLICT (email) DO NOTHING;
