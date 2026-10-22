package com.foodie.delivery.repository;

import com.foodie.delivery.entity.DeliveryShift;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeliveryShiftRepository extends JpaRepository<DeliveryShift, UUID> {

    List<DeliveryShift> findAllByOrderByCreatedAtDesc();

    List<DeliveryShift> findByStatusOrderByCreatedAtDesc(String status);

    List<DeliveryShift> findByZoneIdAndStatusOrderByCreatedAtDesc(String zoneId, String status);

    List<DeliveryShift> findByZoneIdOrderByCreatedAtDesc(String zoneId);

    List<DeliveryShift> findByShiftNameIgnoreCaseAndStartTimeAndEndTimeAndStatusNot(
            String shiftName, String startTime, String endTime, String status);

    boolean existsByShiftNameIgnoreCaseAndStartTimeAndEndTimeAndStatusNot(
            String shiftName, String startTime, String endTime, String status);

    Optional<DeliveryShift> findByIdAndStatus(UUID id, String status);
}
