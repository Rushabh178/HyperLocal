-- =============================================================================
-- Indexes JPA annotations can't express (GiST, partial unique). Spring runs this file on every
-- start, after Hibernate's ddl-auto has created the tables
-- (spring.jpa.defer-datasource-initialization=true), so every statement must be idempotent.
-- =============================================================================

-- Spatial indexes for ST_DWithin / ST_Distance on every geometry column.
CREATE INDEX IF NOT EXISTS idx_merchants_location_gist ON merchants USING gist (location);
CREATE INDEX IF NOT EXISTS idx_customer_addresses_location_gist ON customer_addresses USING gist (location);
CREATE INDEX IF NOT EXISTS idx_delivery_agents_location_gist ON delivery_agents USING gist (current_location);
