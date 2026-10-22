package com.foodie.support.repository;

import com.foodie.support.entity.SupportConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportConversationRepository extends JpaRepository<SupportConversation, String> {
    List<SupportConversation> findByCustomerIdOrderByUpdatedAtDesc(String customerId);

    List<SupportConversation> findByRestaurantIdOrderByUpdatedAtDesc(java.util.UUID restaurantId);

    java.util.Optional<SupportConversation> findByIdAndRestaurantId(String id, java.util.UUID restaurantId);

    List<SupportConversation> findByCategoryOrderByUpdatedAtDesc(String category);

    List<SupportConversation> findAllByOrderByUpdatedAtDesc();

    List<SupportConversation> findByZoneIdOrderByUpdatedAtDesc(String zoneId);
}
