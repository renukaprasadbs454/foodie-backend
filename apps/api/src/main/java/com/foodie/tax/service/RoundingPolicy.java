package com.foodie.tax.service;

import java.math.BigDecimal;

public interface RoundingPolicy {
    long roundToPaise(BigDecimal amount);
    long roundTaxAmount(long taxablePaise, BigDecimal rate);
}
