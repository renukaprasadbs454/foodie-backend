package com.foodie.support.service;

import com.foodie.support.entity.SupportConversation;
import com.foodie.support.entity.SupportMessage;
import com.foodie.support.repository.SupportConversationRepository;
import com.foodie.support.repository.SupportMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SupportService {

    @Autowired
    private SupportConversationRepository conversationRepository;

    @Autowired
    private SupportMessageRepository messageRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired(required = false)
    private com.foodie.restaurant.repository.RestaurantRepository restaurantRepository;

    @Autowired(required = false)
    private com.foodie.delivery.repository.DeliveryPartnerRepository deliveryPartnerRepository;

    @Autowired(required = false)
    private com.foodie.order.repository.OrderRepository orderRepository;

    @Autowired(required = false)
    private com.foodie.user.repository.AddressRepository addressRepository;

    @Autowired(required = false)
    private com.foodie.admin.service.AdminZoneService adminZoneService;

    @Autowired(required = false)
    private com.foodie.admin.repository.LocationZoneRepository locationZoneRepository;

    public String resolveZoneId(UUID restaurantId, String orderId, String customerId, String category) {
        // 1. Restaurant
        if (restaurantId != null && restaurantRepository != null) {
            var r = restaurantRepository.findById(restaurantId).orElse(null);
            if (r != null) {
                String zoneId = findZoneForCoordinatesOrCity(
                        r.getLatitude() != null ? r.getLatitude().doubleValue() : null,
                        r.getLongitude() != null ? r.getLongitude().doubleValue() : null,
                        r.getAddress() != null ? r.getAddress().getCity() : null);
                if (zoneId != null) return zoneId;
            }
        }

        // 2. Order
        if (orderId != null && !orderId.isBlank() && orderRepository != null) {
            com.foodie.order.entity.Order order = null;
            try {
                order = orderRepository.findById(UUID.fromString(orderId)).orElse(null);
            } catch (Exception ignored) {
                order = orderRepository.findByOrderNumber(orderId).orElse(null);
            }
            if (order != null) {
                if (order.getRestaurantId() != null && restaurantRepository != null) {
                    var r = restaurantRepository.findById(order.getRestaurantId()).orElse(null);
                    if (r != null) {
                        String zoneId = findZoneForCoordinatesOrCity(
                                r.getLatitude() != null ? r.getLatitude().doubleValue() : null,
                                r.getLongitude() != null ? r.getLongitude().doubleValue() : null,
                                r.getAddress() != null ? r.getAddress().getCity() : null);
                        if (zoneId != null) return zoneId;
                    }
                }
                if (order.getAddressId() != null && addressRepository != null) {
                    var addr = addressRepository.findById(order.getAddressId()).orElse(null);
                    if (addr != null) {
                        String zoneId = findZoneForCoordinatesOrCity(
                                addr.getLatitude() != null ? addr.getLatitude().doubleValue() : null,
                                addr.getLongitude() != null ? addr.getLongitude().doubleValue() : null,
                                addr.getCity());
                        if (zoneId != null) return zoneId;
                    }
                }
            }
        }

        // 3. Delivery partner
        if (("DELIVERY".equalsIgnoreCase(category) || (customerId != null && (customerId.startsWith("DRV-") || customerId.contains("rider")))) && deliveryPartnerRepository != null && customerId != null) {
            try {
                UUID dpUid = UUID.fromString(customerId);
                var dp = deliveryPartnerRepository.findByUserCredentialId(dpUid)
                        .or(() -> deliveryPartnerRepository.findById(dpUid))
                        .orElse(null);
                if (dp != null && dp.getZoneId() != null && !dp.getZoneId().isBlank()) {
                    return dp.getZoneId();
                }
            } catch (Exception ignored) {}
        }

        // 4. Customer addresses
        if (customerId != null && addressRepository != null) {
            try {
                UUID custUid = UUID.fromString(customerId);
                var addrs = addressRepository.findByCustomerIdOrderByCreatedAtAsc(custUid);
                for (var addr : addrs) {
                    String zoneId = findZoneForCoordinatesOrCity(
                            addr.getLatitude() != null ? addr.getLatitude().doubleValue() : null,
                            addr.getLongitude() != null ? addr.getLongitude().doubleValue() : null,
                            addr.getCity());
                    if (zoneId != null) return zoneId;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    public String findZoneForCoordinatesOrCity(Double lat, Double lng, String city) {
        if (locationZoneRepository == null || adminZoneService == null) return null;
        List<com.foodie.admin.entity.LocationZone> zones = locationZoneRepository.findAll();
        for (var zone : zones) {
            if ("DELETED".equalsIgnoreCase(zone.getStatus())) continue;
            if (adminZoneService.isLocationInZone(lat, lng, city, zone)) {
                return zone.getId();
            }
        }
        return null;
    }

    @Transactional
    public SupportConversation getOrCreateConversation(String customerId, String category, String subject,
            String orderId) {
        // Return existing active conversation if any, else create new
        List<SupportConversation> activeConversations = conversationRepository
                .findByCustomerIdOrderByUpdatedAtDesc(customerId)
                .stream().filter(c -> !c.getStatus().equals("RESOLVED") && !c.getStatus().equals("CLOSED"))
                .toList();

        if (!activeConversations.isEmpty()) {
            return activeConversations.get(0);
        }

        SupportConversation conv = new SupportConversation();
        conv.setId("ENQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        conv.setCustomerId(customerId);
        conv.setStatus("AI_ACTIVE");
        conv.setCategory(category);
        conv.setSubject(subject);
        conv.setOrderId(orderId);
        conv.setZoneId(resolveZoneId(null, orderId, customerId, category));

        SupportConversation saved = conversationRepository.save(conv);
        broadcastConversationUpdate(saved);
        return saved;
    }

    public List<SupportConversation> getAllAdminConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc();
    }

    public List<SupportConversation> getAllAdminConversations(String zoneId) {
        if (zoneId == null || zoneId.isBlank() || zoneId.equalsIgnoreCase("ALL")) {
            return conversationRepository.findAllByOrderByUpdatedAtDesc();
        }
        return conversationRepository.findByZoneIdOrderByUpdatedAtDesc(zoneId);
    }

    public List<SupportMessage> getAllAdminMessages(String zoneId) {
        if (zoneId == null || zoneId.isBlank() || zoneId.equalsIgnoreCase("ALL")) {
            return messageRepository.findAll();
        }
        return messageRepository.findByZoneIdOrderByCreatedAtDesc(zoneId);
    }

    public List<SupportConversation> getCustomerConversations(String customerId) {
        return conversationRepository.findByCustomerIdOrderByUpdatedAtDesc(customerId);
    }

    public Optional<SupportConversation> getConversation(String id) {
        return conversationRepository.findById(id);
    }

    public List<SupportMessage> getMessages(String conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Transactional
    public SupportMessage addMessage(String conversationId, String senderType, String senderId, String senderName,
            String content) {
        SupportConversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        if (conv.getZoneId() == null || conv.getZoneId().isBlank()) {
            String resolvedZone = resolveZoneId(conv.getRestaurantId(), conv.getOrderId(), conv.getCustomerId(), conv.getCategory());
            if (resolvedZone != null) {
                conv.setZoneId(resolvedZone);
            }
        }

        SupportMessage msg = new SupportMessage();
        msg.setId("MSG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        msg.setConversationId(conversationId);
        msg.setSenderType(senderType); // "CUSTOMER", "AI", "AGENT", "SYSTEM"
        msg.setSenderId(senderId);
        msg.setSenderName(senderName);
        msg.setMessageType("TEXT");
        msg.setContent(content);
        msg.setZoneId(conv.getZoneId());

        SupportMessage savedMsg = messageRepository.save(msg);

        conv.setLastMessageAt(Instant.now());
        conv.setUpdatedAt(Instant.now());

        // Reopen resolved conversation if customer or restaurant sends a new message
        if (("CUSTOMER".equalsIgnoreCase(senderType) || "RESTAURANT".equalsIgnoreCase(senderType))
                && ("RESOLVED".equalsIgnoreCase(conv.getStatus()) || "CLOSED".equalsIgnoreCase(conv.getStatus()))) {
            conv.setStatus("IN_PROGRESS");
            conv.setResolvedAt(null);
        }

        // If agent responds, ensure status is AGENT_ACTIVE
        if ("AGENT".equalsIgnoreCase(senderType) && !"RESOLVED".equalsIgnoreCase(conv.getStatus())) {
            conv.setStatus("AGENT_ACTIVE");
            if (conv.getAssignedAgentId() == null) {
                conv.setAssignedAgentId(senderId);
            }
        }

        SupportConversation savedConv = conversationRepository.save(conv);

        // Broadcast realtime events
        broadcastMessage(savedMsg);
        broadcastConversationUpdate(savedConv);

        return savedMsg;
    }

    @Transactional
    public SupportConversation escalateToAgent(String conversationId) {
        SupportConversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        conv.setStatus("WAITING_FOR_AGENT");
        conv.setUpdatedAt(Instant.now());
        SupportConversation saved = conversationRepository.save(conv);

        broadcastConversationUpdate(saved);

        // Add implicit system message
        addMessage(conversationId, "SYSTEM", "SYSTEM", "Foodie System", "Escalated to human agent. Please wait.");

        return saved;
    }

    @Transactional
    public SupportConversation assignAgent(String conversationId, String agentId) {
        SupportConversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        conv.setStatus("ASSIGNED");
        conv.setAssignedAgentId(agentId);
        conv.setUpdatedAt(Instant.now());
        SupportConversation saved = conversationRepository.save(conv);

        broadcastConversationUpdate(saved);
        return saved;
    }

    @Transactional
    public SupportConversation resolveConversation(String conversationId) {
        SupportConversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        conv.setStatus("RESOLVED");
        conv.setResolvedAt(Instant.now());
        conv.setUpdatedAt(Instant.now());
        SupportConversation saved = conversationRepository.save(conv);

        broadcastConversationUpdate(saved);
        return saved;
    }

    @Transactional
    public SupportConversation createRestaurantConversation(UUID restaurantId, String restaurantName, String subject,
            String orderId, String initialMessage) {
        SupportConversation conv = new SupportConversation();
        conv.setId("ENQ-REST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        conv.setCustomerId(restaurantId.toString());
        conv.setRestaurantId(restaurantId);
        conv.setRestaurantName(restaurantName);
        conv.setCategory("RESTAURANT");
        conv.setStatus("OPEN");
        conv.setSubject((subject != null && !subject.isBlank()) ? subject : "Restaurant Support Enquiry");
        conv.setOrderId(orderId);
        conv.setCreatedAt(Instant.now());
        conv.setUpdatedAt(Instant.now());
        conv.setZoneId(resolveZoneId(restaurantId, orderId, restaurantId.toString(), "RESTAURANT"));

        SupportConversation saved = conversationRepository.save(conv);

        if (initialMessage != null && !initialMessage.isBlank()) {
            addMessage(saved.getId(), "RESTAURANT", restaurantId.toString(), restaurantName, initialMessage);
        }

        broadcastConversationUpdate(saved);
        return saved;
    }

    public List<SupportConversation> getRestaurantConversations(UUID restaurantId) {
        return conversationRepository.findByRestaurantIdOrderByUpdatedAtDesc(restaurantId);
    }

    public Optional<SupportConversation> getRestaurantConversation(String conversationId, UUID restaurantId) {
        return conversationRepository.findByIdAndRestaurantId(conversationId, restaurantId);
    }

    @Transactional
    public List<SupportMessage> getRestaurantMessages(String conversationId, UUID restaurantId) {
        conversationRepository.findByIdAndRestaurantId(conversationId, restaurantId)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Unauthorized: Conversation does not belong to your restaurant"));

        // Mark incoming agent messages as read by restaurant
        messageRepository.markAsRead(conversationId, "RESTAURANT", Instant.now());

        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Transactional
    public SupportMessage addRestaurantMessage(String conversationId, UUID restaurantId, String restaurantName, String content) {
        SupportConversation conv = conversationRepository.findByIdAndRestaurantId(conversationId, restaurantId)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Unauthorized: Conversation does not belong to your restaurant"));

        // If conversation was resolved, reopen it to IN_PROGRESS upon new message from restaurant
        if ("RESOLVED".equals(conv.getStatus()) || "CLOSED".equals(conv.getStatus())) {
            conv.setStatus("IN_PROGRESS");
            conv.setResolvedAt(null);
            conversationRepository.save(conv);
        }

        return addMessage(conversationId, "RESTAURANT", restaurantId.toString(), restaurantName, content);
    }

    @Transactional
    public SupportConversation updateConversationStatus(String conversationId, String newStatus) {
        SupportConversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        conv.setStatus(newStatus);
        conv.setUpdatedAt(Instant.now());
        if ("RESOLVED".equals(newStatus)) {
            conv.setResolvedAt(Instant.now());
        } else if ("OPEN".equals(newStatus) || "IN_PROGRESS".equals(newStatus)) {
            conv.setResolvedAt(null);
        }

        SupportConversation saved = conversationRepository.save(conv);
        broadcastConversationUpdate(saved);
        return saved;
    }

    @Transactional
    public void markMessagesReadByAdmin(String conversationId) {
        messageRepository.markAsRead(conversationId, "AGENT", Instant.now());
    }

    private void broadcastMessage(SupportMessage msg) {
        messagingTemplate.convertAndSend("/topic/support/" + msg.getConversationId(), msg);
    }

    private void broadcastConversationUpdate(SupportConversation conv) {
        messagingTemplate.convertAndSend("/topic/admin/support", conv);
        if (conv.getRestaurantId() != null) {
            messagingTemplate.convertAndSend("/topic/restaurant/" + conv.getRestaurantId() + "/support", conv);
        }
        messagingTemplate.convertAndSend("/topic/customer/" + conv.getCustomerId() + "/support", conv);
    }
}
