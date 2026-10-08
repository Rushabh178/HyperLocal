-- =============================================================================
-- Indexes JPA annotations can't express (GiST, partial unique). Spring runs this file on every
-- start, after Hibernate's ddl-auto has created the tables
-- (spring.jpa.defer-datasource-initialization=true), so every statement must be idempotent.
-- =============================================================================

-- Spatial indexes for ST_DWithin / ST_Distance on every geometry column.
CREATE INDEX IF NOT EXISTS idx_merchants_location_gist ON merchants USING gist (location);
CREATE INDEX IF NOT EXISTS idx_customer_addresses_location_gist ON customer_addresses USING gist (location);
CREATE INDEX IF NOT EXISTS idx_delivery_agents_location_gist ON delivery_agents USING gist (current_location);
CREATE INDEX IF NOT EXISTS idx_route_stops_location_gist ON route_stops USING gist (location);
CREATE INDEX IF NOT EXISTS idx_unified_orders_drop_gist ON unified_orders USING gist (drop_location);

-- A rider has at most one ACTIVE route.
CREATE UNIQUE INDEX IF NOT EXISTS uq_rider_routes_active ON rider_routes (agent_id) WHERE status = 'ACTIVE';

-- At most one pending offer per order, so two riders can never hold the same order at once.
CREATE UNIQUE INDEX IF NOT EXISTS uq_task_offers_open_per_order ON task_offers (order_id) WHERE status = 'OFFERED';
