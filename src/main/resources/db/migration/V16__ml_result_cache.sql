CREATE TABLE ml_result_cache (
    id          BIGINT        AUTO_INCREMENT PRIMARY KEY,
    org_id      BIGINT        NOT NULL,
    cache_key   VARCHAR(50)   NOT NULL,
    json_result LONGTEXT      NOT NULL,
    computed_at DATETIME(6)   NOT NULL,
    CONSTRAINT uq_ml_cache UNIQUE (org_id, cache_key)
);
