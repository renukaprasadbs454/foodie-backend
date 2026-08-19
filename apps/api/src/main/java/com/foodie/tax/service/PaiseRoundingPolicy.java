package com.foodie.tax.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class PaiseRoundingPolicy implements RoundingPolicy {

    @Override
    public long roundToPaise(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    @Override
    public long roundTaxAmount(long taxablePaise, BigDecimal rate) {
        if (taxablePaise <= 0 || rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            return 0L;
        }
        BigDecimal taxable = BigDecimal.valueOf(taxablePaise);
        BigDecimal tax = taxable.multiply(rate).setScale(0, RoundingMode.HALF_UP);
        return tax.longValueExact();
    }
}
