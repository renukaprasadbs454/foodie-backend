package com.foodie.wallet.service.impl;

import com.foodie.common.enums.LedgerEntryType;
import com.foodie.common.enums.LedgerReferenceType;
import com.foodie.common.enums.OrderStatus;
import com.foodie.common.enums.OwnerType;
import com.foodie.wallet.entity.LedgerEntry;
import com.foodie.wallet.entity.WalletAccount;
import com.foodie.wallet.repository.LedgerEntryRepository;
import com.foodie.wallet.repository.WalletAccountRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WalletBackfillRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(WalletBackfillRunner.class);

    private final WalletAccountRepository walletAccountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final jakarta.persistence.EntityManager entityManager;

    public WalletBackfillRunner(
            WalletAccountRepository walletAccountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            jakarta.persistence.EntityManager entityManager) {
        this.walletAccountRepository = walletAccountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting Restaurant Wallet backfill...");

        try {
            @SuppressWarnings("unchecked")
            List<Object[]> orders = entityManager.createQuery(
                    "SELECT o.id, o.restaurantId, o.subtotal, o.taxAmount FROM Order o WHERE o.status IN (:status1, :status2, :status3)")
                    .setParameter("status1", OrderStatus.DELIVERED)
                    .setParameter("status2", OrderStatus.PICKED_UP)
                    .setParameter("status3", OrderStatus.OUT_FOR_DELIVERY)
                    .getResultList();

            for (Object[] row : orders) {
                UUID orderId = (UUID) row[0];
                UUID restaurantId = (UUID) row[1];
                BigDecimal subtotal = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;
                BigDecimal taxFee = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;

                BigDecimal commissionPct = new BigDecimal("18.00");
                try {
                    List<BigDecimal> commList = entityManager.createQuery(
                            "SELECT r.commissionPct FROM Restaurant r WHERE r.id = :rId", BigDecimal.class)
                            .setParameter("rId", restaurantId)
                            .getResultList();
                    if (!commList.isEmpty() && commList.get(0) != null) {
                        commissionPct = commList.get(0);
                    }
                } catch (Exception ignored) {
                }

                BigDecimal commissionAmount = subtotal.multiply(commissionPct).divide(new BigDecimal("100"), 2,
                        RoundingMode.HALF_UP);
                BigDecimal netEarnings = subtotal.subtract(commissionAmount);
                BigDecimal finalEarnings = netEarnings.add(taxFee);

                if (finalEarnings.compareTo(BigDecimal.ZERO) > 0) {
                    try {
                        boolean exists = ledgerEntryRepository.existsByReferenceTypeAndReferenceId(
                                LedgerReferenceType.ORDER_EARNING, orderId);
                        if (exists) {
                            continue;
                        }

                        WalletAccount account = walletAccountRepository
                                .findByOwnerTypeAndOwnerId(OwnerType.RESTAURANT, restaurantId)
                                .orElseGet(() -> walletAccountRepository.save(WalletAccount.open(OwnerType.RESTAURANT, restaurantId)));

                        LedgerEntry entry = LedgerEntry.credit(
                                account.getId(),
                                finalEarnings,
                                LedgerReferenceType.ORDER_EARNING,
                                orderId);
                        ledgerEntryRepository.save(entry);

                        account.applyCredit(finalEarnings);
                        walletAccountRepository.save(account);

                        log.info("Backfilled for delivered order {} -> +{}", orderId, finalEarnings);
                    } catch (Exception ex) {
                        log.error("Failed to backfill for order {} restaurant {}. Error: {}", orderId, restaurantId, ex.getMessage());
                    }
                }
            }

            // Recalibrate all restaurant wallet balances to match ledger sums
            List<WalletAccount> restaurantAccounts = entityManager.createQuery(
                    "SELECT w FROM WalletAccount w WHERE w.ownerType = :ownerType", WalletAccount.class)
                    .setParameter("ownerType", OwnerType.RESTAURANT)
                    .getResultList();

            for (WalletAccount acc : restaurantAccounts) {
                @SuppressWarnings("unchecked")
                List<Object[]> sums = entityManager.createQuery(
                        "SELECT l.entryType, SUM(l.amount) FROM LedgerEntry l WHERE l.walletAccountId = :wId GROUP BY l.entryType")
                        .setParameter("wId", acc.getId())
                        .getResultList();

                BigDecimal credits = BigDecimal.ZERO;
                BigDecimal debits = BigDecimal.ZERO;
                for (Object[] s : sums) {
                    LedgerEntryType type = (LedgerEntryType) s[0];
                    BigDecimal amt = s[1] != null ? (BigDecimal) s[1] : BigDecimal.ZERO;
                    if (type == LedgerEntryType.CREDIT) {
                        credits = credits.add(amt);
                    } else if (type == LedgerEntryType.DEBIT) {
                        debits = debits.add(amt);
                    }
                }
                BigDecimal trueBalance = credits.subtract(debits).setScale(2, RoundingMode.HALF_UP);
                acc.setBalance(trueBalance);
                walletAccountRepository.save(acc);
            }

            log.info("Finished Restaurant Wallet backfill!");
        } catch (Exception fatal) {
            log.error("Wallet backfill runner failed entirely: ", fatal);
        }
    }
}
