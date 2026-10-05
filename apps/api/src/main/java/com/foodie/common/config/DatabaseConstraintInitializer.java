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
    }
}
