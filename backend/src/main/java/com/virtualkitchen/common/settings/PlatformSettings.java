package com.virtualkitchen.common.settings;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

/** Admin-editable platform switches and numbers (table platform_settings), with defaults from configuration. */
@Component
public class PlatformSettings {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PlatformSettings(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public String get(String key) {
        return jdbc.query("SELECT value FROM platform_settings WHERE key = ?", rs -> rs.next() ? rs.getString(1) : null, key);
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key);
        try {
            return value == null ? defaultValue : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public void set(String key, String value, UUID adminId) {
        jdbc.update("""
                INSERT INTO platform_settings (key, value, updated_by, updated_at) VALUES (?, ?, ?, ?)
                ON CONFLICT (key) DO UPDATE SET value = excluded.value, updated_by = excluded.updated_by, updated_at = excluded.updated_at
                """, key, value, adminId, Timestamp.from(clock.instant()));
    }
}
