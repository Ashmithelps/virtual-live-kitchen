-- Baseline migration — schema starts here.
-- All future tables are added in subsequent versioned migrations.
-- Never edit this file once it has been applied.

-- Enforce UTC for all sessions to complement Hibernate's jdbc.time_zone=UTC setting.
SET TIME ZONE 'UTC';
