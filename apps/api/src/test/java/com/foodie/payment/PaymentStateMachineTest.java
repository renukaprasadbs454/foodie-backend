package com.foodie.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodie.common.enums.PaymentStatus;
import com.foodie.common.exception.UnprocessableEntityException;
import com.foodie.payment.state.PaymentStateMachine;
import org.junit.jupiter.api.Test;

class PaymentStateMachineTest {

    @Test
    void isValidTransition_validTransitions_returnsTrue() {
        assertThat(PaymentStateMachine.isValidTransition(PaymentStatus.CREATED, PaymentStatus.PENDING)).isTrue();
        assertThat(PaymentStateMachine.isValidTransition(PaymentStatus.PENDING, PaymentStatus.AUTHORIZED)).isTrue();
        assertThat(PaymentStateMachine.isValidTransition(PaymentStatus.PENDING, PaymentStatus.CAPTURED)).isTrue();
        assertThat(PaymentStateMachine.isValidTransition(PaymentStatus.PENDING, PaymentStatus.FAILED)).isTrue();
        assertThat(PaymentStateMachine.isValidTransition(PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED)).isTrue();
        assertThat(PaymentStateMachine.isValidTransition(PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED)).isTrue();
        assertThat(PaymentStateMachine.isValidTransition(PaymentStatus.CAPTURED, PaymentStatus.REFUNDED)).isTrue();
        assertThat(PaymentStateMachine.isValidTransition(PaymentStatus.FAILED, PaymentStatus.PENDING)).isTrue();
    }

    @Test
    void isValidTransition_invalidTransitions_returnsFalse() {
        assertThat(PaymentStateMachine.isValidTransition(PaymentStatus.FAILED, PaymentStatus.CAPTURED)).isFalse();
        assertThat(PaymentStateMachine.isValidTransition(PaymentStatus.REFUNDED, PaymentStatus.PENDING)).isFalse();
        assertThat(PaymentStateMachine.isValidTransition(PaymentStatus.REFUNDED, PaymentStatus.CAPTURED)).isFalse();
    }

    @Test
    void validateTransition_invalidTransition_throwsUnprocessableEntityException() {
        assertThatThrownBy(() -> PaymentStateMachine.validateTransition(PaymentStatus.FAILED, PaymentStatus.CAPTURED))
                .isInstanceOf(UnprocessableEntityException.class);
    }
}
