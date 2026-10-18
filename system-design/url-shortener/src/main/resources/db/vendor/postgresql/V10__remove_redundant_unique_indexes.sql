-- PostgreSQL creates an index for every UNIQUE constraint. These two additional
-- indexes repeat the same key and only add write amplification and vacuum work.
DROP INDEX IF EXISTS idx_urls_short_code;
DROP INDEX IF EXISTS idx_enterprise_api_keys_key_hash;
