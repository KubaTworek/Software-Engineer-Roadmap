ALTER TABLE catalog.supplier_import_runs ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'RUNNING'
    CHECK (status IN ('RUNNING','FAILED','CANCELLED','COMPLETE'));
ALTER TABLE catalog.supplier_import_runs ADD COLUMN failure VARCHAR(255);
ALTER TABLE catalog.supplier_import_runs ADD COLUMN expected_rows BIGINT;
UPDATE catalog.supplier_import_runs SET status='CANCELLED' WHERE cancelled;
CREATE TABLE catalog.supplier_import_rejections (
    run_id UUID NOT NULL REFERENCES catalog.supplier_import_runs(id),
    line_number BIGINT NOT NULL,
    reason VARCHAR(80) NOT NULL,
    PRIMARY KEY(run_id,line_number)
);
-- Przełączenie wskaźnika publikuje cały snapshot, nigdy pojedynczą porcję.
-- Jest to feed jednego dostawcy, a nie aktualizacja agregatów Product.
CREATE TABLE catalog.supplier_active_feed (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK(singleton),
    run_id UUID NOT NULL REFERENCES catalog.supplier_import_runs(id)
);
