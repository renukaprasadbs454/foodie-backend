package com.foodie.delivery;

import com.foodie.auth.exception.InvalidOtpException;
import com.foodie.common.enums.DeliveryAssignmentStatus;
import com.foodie.common.enums.PaymentStatus;
import com.foodie.common.enums.VehicleType;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.common.exception.UnprocessableEntityException;
import com.foodie.delivery.dto.request.VerifyOtpRequestDto;
import com.foodie.delivery.dto.response.DeliveryAssignmentResponseDto;
import com.foodie.delivery.entity.DeliveryAssignment;
import com.foodie.delivery.entity.DeliveryPartner;
import com.foodie.delivery.mapper.DeliveryMapper;
import com.foodie.delivery.repository.DeliveryAssignmentRepository;
import com.foodie.delivery.repository.DeliveryPartnerRepository;
import com.foodie.delivery.service.impl.DeliveryServiceImpl;
import com.foodie.payment.entity.Payment;
import com.foodie.payment.repository.PaymentRepository;
import com.foodie.shared.contract.OrderDeliveryPort;
import com.foodie.shared.event.DeliveryCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryVerificationFlowTest {

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Mock
    private DeliveryAssignmentRepository deliveryAssignmentRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderDeliveryPort orderDeliveryPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Spy
    private DeliveryMapper deliveryMapper = new DeliveryMapper();

    @InjectMocks
    private DeliveryServiceImpl deliveryService;

    private UUID userCredentialId;
    private UUID orderId;
    private UUID assignmentId;
    private DeliveryPartner partner;
    private DeliveryAssignment assignment;

    @BeforeEach
    void setUp() {
        userCredentialId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        assignmentId = UUID.randomUUID();

        partner = DeliveryPartner.create(userCredentialId, "Ravi Kumar", VehicleType.BIKE, "KA01AB1234");
        partner.verifyKyc();
        assignment = DeliveryAssignment.createOffered(
                orderId,
                partner,
                "pickupHash",
                "deliveryHash"
        );
        assignment.accept();
        assignment.markPickupVerified(); // Now in PICKED_UP status
    }

    @Test
    void testVerifyDelivery_Success_OnlinePayment() {
        when(deliveryPartnerRepository.findByUserCredentialId(userCredentialId))
                .thenReturn(Optional.of(partner));
        when(deliveryAssignmentRepository.findByIdAndDeliveryPartnerId(assignmentId, partner.getId()))
                .thenReturn(Optional.of(assignment));
        when(passwordEncoder.matches("654321", "deliveryHash")).thenReturn(true);

        Payment onlinePayment = Payment.initiate(
                orderId, "session_123", new BigDecimal("350.00"), BigDecimal.ZERO, "idemp_123");
        onlinePayment.markCaptured("cf_order_123");
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(onlinePayment));

        VerifyOtpRequestDto request = new VerifyOtpRequestDto("654321");

        DeliveryAssignmentResponseDto response = deliveryService.verifyDelivery(userCredentialId, assignmentId, request);

        assertThat(response).isNotNull();
        assertThat(assignment.getStatus()).isEqualTo(DeliveryAssignmentStatus.DELIVERED);
        assertThat(assignment.getDeliveredVerifiedAt()).isNotNull();

        // Partner cash-in-hand should NOT increase for online payment
        assertThat(partner.getCashInHand()).isEqualTo(BigDecimal.ZERO);

        verify(orderDeliveryPort).markDelivered(orderId);
        verify(deliveryAssignmentRepository).save(assignment);
        verify(eventPublisher).publishEvent(any(DeliveryCompletedEvent.class));
    }

    @Test
    void testVerifyDelivery_Success_CodPayment() {
        when(deliveryPartnerRepository.findByUserCredentialId(userCredentialId))
                .thenReturn(Optional.of(partner));
        when(deliveryAssignmentRepository.findByIdAndDeliveryPartnerId(assignmentId, partner.getId()))
                .thenReturn(Optional.of(assignment));
        when(passwordEncoder.matches("654321", "deliveryHash")).thenReturn(true);

        Payment codPayment = Payment.initiate(
                orderId, null, new BigDecimal("450.00"), BigDecimal.ZERO, "idemp_cod");
        // Status remains PENDING for COD before handover
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(codPayment));

        VerifyOtpRequestDto request = new VerifyOtpRequestDto("654321");

        DeliveryAssignmentResponseDto response = deliveryService.verifyDelivery(userCredentialId, assignmentId, request);

        assertThat(response).isNotNull();
        assertThat(assignment.getStatus()).isEqualTo(DeliveryAssignmentStatus.DELIVERED);

        // Partner cash-in-hand MUST increase for COD payment
        assertThat(partner.getCashInHand()).isEqualByComparingTo("450.00");

        verify(deliveryPartnerRepository).save(partner);
        verify(orderDeliveryPort).markDelivered(orderId);
        verify(deliveryAssignmentRepository).save(assignment);
        verify(eventPublisher).publishEvent(any(DeliveryCompletedEvent.class));
    }

    @Test
    void testVerifyDelivery_InvalidOtp_ThrowsInvalidOtpException() {
        when(deliveryPartnerRepository.findByUserCredentialId(userCredentialId))
                .thenReturn(Optional.of(partner));
        when(deliveryAssignmentRepository.findByIdAndDeliveryPartnerId(assignmentId, partner.getId()))
                .thenReturn(Optional.of(assignment));
        when(passwordEncoder.matches("999999", "deliveryHash")).thenReturn(false);

        VerifyOtpRequestDto request = new VerifyOtpRequestDto("999999");

        assertThatThrownBy(() -> deliveryService.verifyDelivery(userCredentialId, assignmentId, request))
                .isInstanceOf(InvalidOtpException.class);

        // Status should remain PICKED_UP
        assertThat(assignment.getStatus()).isEqualTo(DeliveryAssignmentStatus.PICKED_UP);
        verify(orderDeliveryPort, never()).markDelivered(any());
        verify(eventPublisher, never()).publishEvent(any(DeliveryCompletedEvent.class));
    }

    @Test
    void testVerifyDelivery_IllegalStatus_ThrowsUnprocessableEntityException() {
        // Assignment is in ACCEPTED status (not yet PICKED_UP)
        DeliveryAssignment acceptedAssignment = DeliveryAssignment.createOffered(
                orderId, partner, "pickupHash", "deliveryHash");
        acceptedAssignment.accept();

        when(deliveryPartnerRepository.findByUserCredentialId(userCredentialId))
                .thenReturn(Optional.of(partner));
        when(deliveryAssignmentRepository.findByIdAndDeliveryPartnerId(assignmentId, partner.getId()))
                .thenReturn(Optional.of(acceptedAssignment));

        VerifyOtpRequestDto request = new VerifyOtpRequestDto("123456");

        assertThatThrownBy(() -> deliveryService.verifyDelivery(userCredentialId, assignmentId, request))
                .isInstanceOf(UnprocessableEntityException.class);

        verify(orderDeliveryPort, never()).markDelivered(any());
        verify(eventPublisher, never()).publishEvent(any(DeliveryCompletedEvent.class));
    }

    @Test
    void testVerifyDelivery_AssignmentNotFound_ThrowsResourceNotFoundException() {
        when(deliveryPartnerRepository.findByUserCredentialId(userCredentialId))
                .thenReturn(Optional.of(partner));
        when(deliveryAssignmentRepository.findByIdAndDeliveryPartnerId(assignmentId, partner.getId()))
                .thenReturn(Optional.empty());

        VerifyOtpRequestDto request = new VerifyOtpRequestDto("123456");

        assertThatThrownBy(() -> deliveryService.verifyDelivery(userCredentialId, assignmentId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
