CREATE TABLE IF NOT EXISTS model_predictions_audit (
  id UUID PRIMARY KEY,
  model_name VARCHAR(120) NOT NULL,
  model_version VARCHAR(120) NOT NULL,
  aggregate_id UUID,
  city_id VARCHAR(80),
  input_payload JSONB NOT NULL,
  output_payload JSONB NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS region_routing_audit (
  id UUID PRIMARY KEY,
  aggregate_id UUID,
  aggregate_type VARCHAR(80),
  home_region VARCHAR(80) NOT NULL,
  routed_region VARCHAR(80) NOT NULL,
  consistency_mode VARCHAR(120) NOT NULL,
  reason VARCHAR(240),
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_model_predictions_audit_aggregate ON model_predictions_audit(aggregate_id);
CREATE INDEX IF NOT EXISTS idx_region_routing_audit_aggregate ON region_routing_audit(aggregate_id);
