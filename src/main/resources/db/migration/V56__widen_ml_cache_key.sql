-- V56__widen_ml_cache_key.sql
-- Manager-scoped ML cache keys end with "_MGR" + the manager id. With UUID ids
-- (V55, ADR-0009) the longest one, AGENT_INSIGHTS_24_MGR<uuid>, is 57
-- characters: VARCHAR(50) would reject every manager's cached result.
ALTER TABLE ml_result_cache MODIFY cache_key VARCHAR(100) NOT NULL;
