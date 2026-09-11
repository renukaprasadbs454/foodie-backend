package com.foodie.wallet.service.impl;

import com.foodie.common.enums.LedgerReferenceType;
import com.foodie.common.enums.OwnerType;
import com.foodie.wallet.entity.WalletAccount;
import com.foodie.wallet.repository.WalletAccountRepository;
import com.foodie.wallet.service.WalletService;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

@Component
public class WalletBackfillRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(WalletBackfillRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final WalletService walletService;
    private final WalletAccountRepository walletAccountRepository;

    public WalletBackfillRunner(JdbcTemplate jdbcTemplate, WalletService walletService,
            WalletAccountRepository walletAccountRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.walletService = walletService;
        this.walletAccountRepository = walletAccountRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting missing Restuarant Wallet backfill...");

        String query = """
                    SELECT o.id, o.restaurant_id, o.subtotal, o.tax_fee
                    FROM "order" o
                    WHERE o.status = 'DELIVERED'
                    AND NOT EXISTS (
                        SELECT 1 FROM ledger_entry l
                        WHERE l.reference_id = o.id AND l.reference_type = 'ORDER_EARNING'
                    )
                """;

        jdbcTemplate.query(query, new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                UUID orderId = (UUID) rs.getObject("id");
                UUID restaurantId = (UUID) rs.getObject("restaurant_id");
                BigDecimal subtotal = rs.getBigDecimal("subtotal");
                BigDecimal taxFee = rs.getBigDecimal("tax_fee");

                if (subtotal == null) {
                    subtotal = BigDecimal.ZERO;
                }
                if (taxFee == null) {
                    taxFee = BigDecimal.ZERO;
                }

                // Get commission % (default 18.00 if missing)
                BigDecimal commissionPct = jdbcTemplate.queryForObject(
                        "SELECT commission_pct FROM restaurant r WHERE r.id = ?",
                        BigDecimal.class,
                        restaurantId);

                if (commissionPct == null) {
                    commissionPct = new BigDecimal("18.00");
                }

                BigDecimal commissionAmount = subtotal.multiply(commissionPct).divide(new BigDecimal("100"), 2,
                        java.math.RoundingMode.HALF_UP);
                BigDecimal netEarnings = subtotal.subtract(commissionAmount);
                BigDecimal finalEarnings = netEarnings.add(taxFee);

                if (finalEarnings.compareTo(BigDecimal.ZERO) > 0) {
                    try {
                        walletService.credit(OwnerType.RESTAURANT, restaurantId, finalEarnings,
                                LedgerReferenceType.ORDER_EARNING, orderId);
                        log.info("Backfilled wallet for delivered order {} -> +{}", orderId, finalEarnings);
                    } catch (Exception ex) {
                        log.error("Failed to backfill order {} for restaurant {}: {}", orderId, restaurantId,
                                ex.getMessage());
                    }
                }
            }
        });

        // Also fix any Payout Requests that failed to sync UI to Bank because DB had
        // issue
        log.info("Finished Restaurant Wallet backfill!");
    }
}
