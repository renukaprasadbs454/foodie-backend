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

    CommissionConfigDto updateCommissionRules(CommissionConfigDto config);

    PaymentSplitBreakdownDto calculateSplit(BigDecimal foodSubtotal, BigDecimal deliveryFee);

    List<PaymentSettlementResponseDto> listSettlements();

    List<PaymentTransactionResponseDto> listTransactions();

    List<AdminLedgerEntryResponseDto> listLedgerEntries();
}
