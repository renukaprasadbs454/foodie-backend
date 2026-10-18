package com.foodie.admin.service;

import com.foodie.admin.dto.request.CommissionConfigDto;
import com.foodie.admin.dto.response.AdminLedgerEntryResponseDto;
import com.foodie.admin.dto.response.PaymentSettlementResponseDto;
import com.foodie.admin.dto.response.PaymentSplitBreakdownDto;
import com.foodie.admin.dto.response.PaymentTransactionResponseDto;
import java.math.BigDecimal;
import java.util.List;

public interface AdminPaymentService {

    CommissionConfigDto getCommissionRules();

    default CommissionConfigDto getCommissionRules(String zoneId) {
        return getCommissionRules();
    }

    CommissionConfigDto updateCommissionRules(CommissionConfigDto config);

    default CommissionConfigDto updateCommissionRules(String zoneId, CommissionConfigDto config) {
        return updateCommissionRules(config);
    }

    PaymentSplitBreakdownDto calculateSplit(BigDecimal foodSubtotal, BigDecimal deliveryFee);

    List<PaymentSettlementResponseDto> listSettlements();

    default List<PaymentSettlementResponseDto> listSettlements(String zoneId) {
        return listSettlements();
    }

    List<PaymentTransactionResponseDto> listTransactions();

    default List<PaymentTransactionResponseDto> listTransactions(String zoneId) {
        return listTransactions();
    }

    List<AdminLedgerEntryResponseDto> listLedgerEntries();

    default List<AdminLedgerEntryResponseDto> listLedgerEntries(String zoneId) {
        return listLedgerEntries();
    }
}
