package com.foodie.common.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Configuration
public class FlywayFixConfig {
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(DataSource dataSource) {
        return flyway -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                // Clean up stale or conflicting migrations to allow re-application
                stmt.execute("DELETE FROM flyway_schema_history WHERE version IN ('50', '51', '52')");
            } catch (Exception e) {
                // Ignore exception, table might not exist or other issues
            }
            flyway.migrate();
        };
    }
}
