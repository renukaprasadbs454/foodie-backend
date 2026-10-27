package com.foodie.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseConstraintInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConstraintInitializer.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseConstraintInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("Updating wallet_account check constraints for CUSTOMER owner_type...");
            jdbcTemplate.execute("ALTER TABLE \"wallet_account\" DROP CONSTRAINT IF EXISTS chk_wallet_owner_type");
            jdbcTemplate.execute(
                    "ALTER TABLE \"wallet_account\" ADD CONSTRAINT chk_wallet_owner_type CHECK (owner_type IN ('DELIVERY_PARTNER', 'PLATFORM', 'CUSTOMER', 'RESTAURANT'))");
        } catch (Exception e) {
            log.debug("Could not alter wallet_account check constraint: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE \"payment\" DROP CONSTRAINT IF EXISTS chk_payment_amount");
            jdbcTemplate.execute("ALTER TABLE \"payment\" ADD CONSTRAINT chk_payment_amount CHECK (amount >= 0)");
        } catch (Exception e) {
            log.debug("Could not alter payment check constraint: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE \"restaurant\" DROP CONSTRAINT IF EXISTS chk_restaurant_status");
            jdbcTemplate.queryForList(
                "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE UPPER(TABLE_NAME) = 'RESTAURANT' AND CONSTRAINT_TYPE = 'CHECK'",
                String.class
            ).forEach(constraintName -> {
                try {
                    jdbcTemplate.execute("ALTER TABLE \"restaurant\" DROP CONSTRAINT \"" + constraintName + "\"");
                } catch (Exception ignored) {}
            });
        } catch (Exception e) {
            log.debug("Could not alter restaurant check constraint: {}", e.getMessage());
        }

        try {
            jdbcTemplate.queryForList(
                "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE UPPER(TABLE_NAME) = 'DELIVERY_ASSIGNMENT' AND CONSTRAINT_TYPE = 'CHECK'",
                String.class
            ).forEach(constraintName -> {
                try {
                    jdbcTemplate.execute("ALTER TABLE delivery_assignment DROP CONSTRAINT " + constraintName);
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}

        try {
            jdbcTemplate.queryForList(
                "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE UPPER(TABLE_NAME) = 'ORDER' AND CONSTRAINT_TYPE = 'CHECK'",
                String.class
            ).forEach(constraintName -> {
                try {
                    jdbcTemplate.execute("ALTER TABLE \"order\" DROP CONSTRAINT " + constraintName);
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}

        try {
            jdbcTemplate.execute("ALTER TABLE delivery_assignment ALTER COLUMN status VARCHAR(50)");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("ALTER TABLE \"order\" ALTER COLUMN status VARCHAR(50)");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.update("UPDATE delivery_partner SET is_online = FALSE");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.update("UPDATE delivery_assignment SET status = 'EXPIRED' WHERE status IN ('OFFERED', 'ACCEPTED', 'PICKED_UP')");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.update("UPDATE \"order\" SET status = 'CANCELLED' WHERE status IN ('WAITING_FOR_DELIVERY_PARTNER', 'ACCEPTED', 'PREPARING', 'CONFIRMED')");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("ALTER TABLE delivery_partner ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION");
            jdbcTemplate.execute("ALTER TABLE delivery_partner ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION");
            jdbcTemplate.execute("ALTER TABLE delivery_partner ADD COLUMN IF NOT EXISTS zone_id VARCHAR(100)");
            jdbcTemplate.execute("ALTER TABLE delivery_partner ADD COLUMN IF NOT EXISTS zone_name VARCHAR(200)");
            jdbcTemplate.execute("ALTER TABLE delivery_partner ADD COLUMN IF NOT EXISTS zone_verified BOOLEAN DEFAULT FALSE");
            jdbcTemplate.execute("ALTER TABLE delivery_partner ADD COLUMN IF NOT EXISTS location_approval_status VARCHAR(50) DEFAULT 'NONE'");
            jdbcTemplate.execute("ALTER TABLE \"delivery_partner\" ADD COLUMN IF NOT EXISTS \"location_approval_status\" VARCHAR(50) DEFAULT 'NONE'");
        } catch (Exception ignored) {}

        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS \"unserviceable_request\" (" +
                "\"id\" VARCHAR(100) PRIMARY KEY, " +
                "\"delivery_partner_id\" UUID, " +
                "\"restaurant_name\" VARCHAR(255), " +
                "\"contact_person\" VARCHAR(255), " +
                "\"contact_email\" VARCHAR(255), " +
                "\"contact_phone\" VARCHAR(50), " +
                "\"address\" TEXT, " +
                "\"city_name\" VARCHAR(100), " +
                "\"latitude\" DOUBLE PRECISION, " +
                "\"longitude\" DOUBLE PRECISION, " +
                "\"status\" VARCHAR(50) NOT NULL DEFAULT 'PENDING', " +
                "\"created_at\" TIMESTAMP WITH TIME ZONE, " +
                "\"updated_at\" TIMESTAMP WITH TIME ZONE, " +
                "\"request_source\" VARCHAR(50) NOT NULL DEFAULT 'CUSTOMER', " +
                "\"requester_name\" VARCHAR(255), " +
                "\"requested_area\" TEXT, " +
                "\"zone_id\" VARCHAR(100), " +
                "\"zone_name\" VARCHAR(200), " +
                "\"restaurant_id\" UUID, " +
                "\"distance_from_zone_km\" DOUBLE PRECISION, " +
                "\"submitted_documents\" TEXT)");
            log.info("Ensured \"unserviceable_request\" table exists.");
        } catch (Exception e) {
            log.warn("Could not ensure \"unserviceable_request\" table: {}", e.getMessage());
        }

        String[] alterStatements = new String[] {
            "ALTER TABLE restaurant ADD COLUMN IF NOT EXISTS out_of_zone BOOLEAN DEFAULT FALSE",
            "ALTER TABLE restaurant ADD COLUMN IF NOT EXISTS nearest_zone_distance_km DOUBLE PRECISION",
            "ALTER TABLE \"restaurant\" ADD COLUMN IF NOT EXISTS \"out_of_zone\" BOOLEAN DEFAULT FALSE",
            "ALTER TABLE \"restaurant\" ADD COLUMN IF NOT EXISTS \"nearest_zone_distance_km\" DOUBLE PRECISION",
            "ALTER TABLE unserviceable_request ADD COLUMN IF NOT EXISTS restaurant_id UUID",
            "ALTER TABLE unserviceable_request ADD COLUMN IF NOT EXISTS distance_from_zone_km DOUBLE PRECISION",
            "ALTER TABLE \"unserviceable_request\" ADD COLUMN IF NOT EXISTS \"restaurant_id\" UUID",
            "ALTER TABLE \"unserviceable_request\" ADD COLUMN IF NOT EXISTS \"distance_from_zone_km\" DOUBLE PRECISION",
            "ALTER TABLE \"unserviceable_request\" ADD COLUMN IF NOT EXISTS distance_from_zone_km DOUBLE PRECISION",
            "ALTER TABLE \"unserviceable_request\" ADD COLUMN IF NOT EXISTS restaurant_id UUID"
        };
        for (String sql : alterStatements) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception ignored) {}
        }
    }
}