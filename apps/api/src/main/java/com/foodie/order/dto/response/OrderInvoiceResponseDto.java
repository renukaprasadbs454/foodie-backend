package com.foodie.order.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderInvoiceResponseDto(
        UUID orderId,
        String orderNumber,
        String restaurantName,
        String customerName,
        String customerPhone,
        String orderDate,
        String orderTime,
        Instant placedAt,
        List<InvoiceItemDto> items,
        BigDecimal totalAmount
) {
    public record InvoiceItemDto(
            UUID menuItemId,
            String name,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal amount
    ) {}
}
