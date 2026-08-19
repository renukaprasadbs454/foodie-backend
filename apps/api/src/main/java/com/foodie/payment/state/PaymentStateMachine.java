package com.foodie.payment.state;

import com.foodie.common.enums.PaymentStatus;
import com.foodie.common.exception.ErrorCode;
import com.foodie.common.exception.UnprocessableEntityException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = Map.of(
            PaymentStatus.CREATED, EnumSet.of(PaymentStatus.PENDING, PaymentStatus.FAILED),
            PaymentStatus.PENDING, EnumSet.of(PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED, PaymentStatus.FAILED),
            PaymentStatus.AUTHORIZED, EnumSet.of(PaymentStatus.CAPTURED, PaymentStatus.FAILED),
            PaymentStatus.CAPTURED, EnumSet.of(PaymentStatus.REFUND_PENDING, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED),
            PaymentStatus.FAILED, EnumSet.of(PaymentStatus.PENDING), // Re-initiation of failed payment
            PaymentStatus.REFUND_PENDING, EnumSet.of(PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED, PaymentStatus.CAPTURED),
            PaymentStatus.PARTIALLY_REFUNDED, EnumSet.of(PaymentStatus.REFUND_PENDING, PaymentStatus.REFUNDED),
            PaymentStatus.REFUNDED, EnumSet.noneOf(PaymentStatus.class)
    );

    private PaymentStateMachine() {
    }

    public static boolean isValidTransition(PaymentStatus from, PaymentStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true; // Idempotent same-state check
        }
        Set<PaymentStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public static void validateTransition(PaymentStatus from, PaymentStatus to) {
        if (!isValidTransition(from, to)) {
            throw new UnprocessableEntityException(
                    ErrorCode.PAYMENT_STATE_TRANSITION_NOT_ALLOWED,
                    "Payment state transition from " + from + " to " + to + " is not permitted."
            );
        }
    }
}
