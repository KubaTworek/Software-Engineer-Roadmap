-- Import jest własnością Catalog. Efekt porcji i checkpoint mają jedną transakcję.
CREATE TABLE catalog.supplier_import_runs (
    id UUID PRIMARY KEY,
    source_sha256 VARCHAR(64) NOT NULL,
    next_line BIGINT NOT NULL DEFAULT 0 CHECK(next_line >= 0),
    cancelled BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE catalog.supplier_import_items (
    run_id UUID NOT NULL REFERENCES catalog.supplier_import_runs(id),
    line_number BIGINT NOT NULL,
    sku VARCHAR(120) NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK(amount >= 0),
    PRIMARY KEY(run_id, line_number),
    UNIQUE(run_id, sku)
);
