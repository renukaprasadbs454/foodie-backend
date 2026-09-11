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
    private final jakarta.persistence.EntityManager entityManager;

    public WalletBackfillRunner(JdbcTemplate jdbcTemplate, WalletService walletService,
            WalletAccountRepository walletAccountRepository, jakarta.persistence.EntityManager entityManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.walletService = walletService;
        this.walletAccountRepository = walletAccountRepository;
        this.entityManager = entityManager;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting missing Restuarant Wallet backfill...");

        org.springframework.transaction.support.TransactionTemplate txTemplate = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbcTemplate.getDataSource()));

        @SuppressWarnings("unchecked")
        java.util.List<Object[]> orders = entityManager.createQuery(
                "SELECT o.id, o.restaurantId, o.subtotal, o.taxAmount FROM Order o WHERE o.status = com.foodie.common.enums.OrderStatus.DELIVERED")
                .getResultList();

        for (Object[] row : orders) {
            UUID orderId = (UUID) row[0];
            UUID restaurantId = (UUID) row[1];
            BigDecimal subtotal = (BigDecimal) row[2];
            BigDecimal taxFee = (BigDecimal) row[3];

            if (subtotal == null)
                subtotal = BigDecimal.ZERO;
            if (taxFee == null)
                taxFee = BigDecimal.ZERO;

            // Check if ledger entry exists
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT count(1) FROM ledger_entry WHERE reference_id = ? AND reference_type = 'ORDER_EARNING'",
                    Integer.class, orderId);
            if (count != null && count > 0)
                continue;

            // Get commission %
            BigDecimal commissionPct = jdbcTemplate.queryForObject(
                    "SELECT commission_pct FROM restaurant r WHERE r.id = ?",
                    BigDecimal.class, restaurantId);

            if (commissionPct == null)
                commissionPct = new BigDecimal("18.00");

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

        // Also fix any Payout Requests that failed to sync UI to Bank because DB had
        // issue
        log.info("Finished Restaurant Wallet backfill!");
    }
}
