package com.foodie.wallet.repository;

import com.foodie.common.enums.PayoutStatus;
import com.foodie.wallet.entity.Payout;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    @Query("""
            select coalesce(sum(p.amount), 0)
            from Payout p
            where p.walletAccountId = :walletAccountId
              and p.status in :statuses
            """)
    BigDecimal sumAmountByWalletAccountIdAndStatusIn(
            @Param("walletAccountId") UUID walletAccountId,
            @Param("statuses") Collection<PayoutStatus> statuses
    );

    @Query("""
            select coalesce(sum(p.amount), 0)
            from Payout p
            where p.walletAccountId = :walletAccountId
              and p.status = 'COMPLETED'
            """)
    BigDecimal sumCompletedAmountByWalletAccountId(@Param("walletAccountId") UUID walletAccountId);

    org.springframework.data.domain.Page<Payout> findByWalletAccountId(UUID walletAccountId, org.springframework.data.domain.Pageable pageable);

    Optional<Payout> findByIdAndWalletAccountId(UUID id, UUID walletAccountId);
}
