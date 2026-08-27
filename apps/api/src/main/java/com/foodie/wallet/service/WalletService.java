package com.foodie.wallet.service;

import com.foodie.common.dto.PaginationMeta;
import com.foodie.common.enums.LedgerReferenceType;
import com.foodie.common.enums.OwnerType;
import com.foodie.wallet.dto.request.CreateDepositOrderRequestDto;
import com.foodie.wallet.dto.request.PayoutRequestDto;
import com.foodie.wallet.dto.request.VerifyDepositRequestDto;
import com.foodie.wallet.dto.response.DepositOrderResponseDto;
import com.foodie.wallet.dto.response.LedgerEntryResponseDto;
import com.foodie.wallet.dto.response.PayoutResponseDto;
import com.foodie.wallet.dto.response.VerifyDepositResponseDto;
import com.foodie.wallet.dto.response.WalletBalanceResponseDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WalletService {

    DepositOrderResponseDto createDepositOrder(UUID userCredentialId, BigDecimal amount);

    VerifyDepositResponseDto verifyAndProcessDeposit(UUID userCredentialId, VerifyDepositRequestDto request);

    com.foodie.wallet.dto.response.CodBalanceResponseDto recordCodCashCollection(UUID userCredentialId, BigDecimal amount);

    com.foodie.wallet.dto.response.CodBalanceResponseDto getCodBalance(UUID userCredentialId);

    WalletBalanceResponseDto getBalance(UUID userCredentialId);

    PageResult<LedgerEntryResponseDto> getLedger(
            UUID userCredentialId,
            int page,
            int size,
            String sort,
            Instant createdAtFrom,
            Instant createdAtTo
    );

    PayoutResponseDto requestPayout(UUID userCredentialId, PayoutRequestDto request, String idempotencyKey);

    PageResult<PayoutResponseDto> getPayoutHistory(UUID userCredentialId, int page, int size);

    PayoutResponseDto getPayoutDetail(UUID userCredentialId, UUID payoutId);

    PayoutResponseDto updatePayoutStatus(UUID payoutId, com.foodie.common.enums.PayoutStatus status, String bankRef, String failureReason);

    WalletBalanceResponseDto getRestaurantBalance(UUID ownerCredentialId);

    PageResult<LedgerEntryResponseDto> getRestaurantLedger(
            UUID ownerCredentialId,
            int page,
            int size,
            String sort,
            Instant createdAtFrom,
            Instant createdAtTo
    );

    PayoutResponseDto requestRestaurantPayout(
            UUID ownerCredentialId,
            PayoutRequestDto request,
            String idempotencyKey
    );

    /** Idempotent CREDIT used by domain event listeners (driver earnings / refund credits). */
    LedgerEntryResponseDto credit(
            OwnerType ownerType,
            UUID ownerId,
            BigDecimal amount,
            LedgerReferenceType referenceType,
            UUID referenceId
    );

    /** Idempotent DEBIT used when payout completion is wired (bank settlement out of Module 9 scope). */
    LedgerEntryResponseDto debit(
            OwnerType ownerType,
            UUID ownerId,
            BigDecimal amount,
            LedgerReferenceType referenceType,
            UUID referenceId
    );

    record PageResult<T>(List<T> items, PaginationMeta pagination) {
    }
}
