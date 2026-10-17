package com.foodie.delivery.repository;

import com.foodie.delivery.entity.DeliveryShiftBooking;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeliveryShiftBookingRepository extends JpaRepository<DeliveryShiftBooking, UUID> {

    List<DeliveryShiftBooking> findByDeliveryPartnerIdOrderByBookedAtDesc(UUID deliveryPartnerId);

    boolean existsByDeliveryPartnerIdAndShiftIdAndStatus(UUID deliveryPartnerId, UUID shiftId, String status);

    List<DeliveryShiftBooking> findByShiftIdAndStatus(UUID shiftId, String status);
}
