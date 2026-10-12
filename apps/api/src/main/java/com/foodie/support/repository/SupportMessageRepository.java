package com.foodie.support.repository;

import com.foodie.support.entity.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface SupportMessageRepository extends JpaRepository<SupportMessage, String> {
    List<SupportMessage> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    long countByConversationIdAndSenderTypeNotAndReadAtIsNull(String conversationId, String senderType);

    @Modifying
    @Query("UPDATE SupportMessage m SET m.readAt = :readAt, m.readStatus = true WHERE m.conversationId = :conversationId AND m.senderType != :excludeSenderType AND m.readAt IS NULL")
    void markAsRead(@Param("conversationId") String conversationId, @Param("excludeSenderType") String excludeSenderType, @Param("readAt") java.time.Instant readAt);
}
