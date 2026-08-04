package com.foodie.delivery.repository;

import com.foodie.delivery.entity.DeliveryPartnerDocument;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryPartnerDocumentRepository extends JpaRepository<DeliveryPartnerDocument, UUID> {
}
