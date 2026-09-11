package com.foodie.wallet.service.impl;

import com.foodie.common.enums.LedgerReferenceType;
import com.foodie.common.enums.OwnerType;
import com.foodie.common.enums.OrderStatus;
import com.foodie.wallet.entity.WalletAccount;
import com.foodie.wallet.repository.WalletAccountRepository;
import com.foodie.wallet.service.WalletService;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
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
    public void run(String... args) {
        log.info("Starting missing Restuarant Wallet backfill...");

        try {
            @SuppressWarnings("unchecked")
            java.util.List<Object[]> orders = entityManager.createQuery(
                    "SELECT o.id, o.restaurantId, o.subtotal, o.taxAmount FROM Order o WHERE o.status = :status")
                    .setParameter("status", OrderStatus.DELIVERED)
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
                BigDecimal commissionPct = null;
                try {
                    commissionPct = jdbcTemplate.queryForObject(
                            "SELECT commission_pct FROM restaurant r WHERE r.id = ?",
                            BigDecimal.class, restaurantId);
                } catch (Exception ignored) {
                }

                if (commissionPct == null)
                    commissionPct = new BigDecimal("18.00");

                BigDecimal commissionAmount = subtotal.multiply(commissionPct).divide(new BigDecimal("100"), 2,
                        java.math.RoundingMode.HALF_UP);
                BigDecimal netEarnings = subtotal.subtract(commissionAmount);
                BigDecimal finalEarnings = netEarnings.add(taxFee);

                if (finalEarnings.compareTo(BigDecimal.ZERO) > 0) {
                    try {
                        // 1. Ensure WalletAccount exists natively
                        jdbcTemplate.update(
                                """
                                            INSERT INTO wallet_account (id, owner_type, owner_id, balance, version, created_at, updated_at)
                                            VALUES (?, 'RESTAURANT', ?, 0, 0, now(), now())
                                            ON CONFLICT (owner_type, owner_id) DO NOTHING
                                        """,
                                UUID.randomUUID(), restaurantId);

                        // 2. Insert Ledger Entry natively
                        jdbcTemplate.update(
                                """
                                            INSERT INTO ledger_entry (id, wallet_account_id, entry_type, amount, reference_type, reference_id, created_at)
                                            SELECT ?, id, 'CREDIT', ?, 'ORDER_EARNING', ?, now()
                                            FROM wallet_account WHERE owner_type = 'RESTAURANT' AND owner_id = ?
                                        """,
                                UUID.randomUUID(), finalEarnings, orderId, restaurantId);

                        // 3. Update Balance atomically natively
                        jdbcTemplate.update("""
                                    UPDATE wallet_account SET balance = balance + ?, updated_at = now()
                                    WHERE owner_type = 'RESTAURANT' AND owner_id = ?
                                """, finalEarnings, restaurantId);

                        log.info("Backfilled wallet natively for delivered order {} -> +{}", orderId, finalEarnings);
                    } catch (Exception ex) {
                        log.error("Failed native backfill for {} {}: {}", orderId, restaurantId, ex.getMessage());
                    }
                }
            }
            log.info("Finished Restaurant Wallet backfill!");
        } catch (Exception fatal) {
            log.error("Wallet backfill runner failed entirely, but bypassing crash so application can start: ", fatal);
        }
    }
}
