package com.foodie.review.service.impl;

import com.foodie.common.dto.PaginationMeta;
import com.foodie.common.enums.OrderStatus;
import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.ConflictException;
import com.foodie.common.exception.ErrorCode;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.common.exception.UnprocessableEntityException;
import com.foodie.review.dto.request.SubmitReviewRequestDto;
import com.foodie.review.dto.response.RestaurantReviewItemDto;
import com.foodie.review.dto.response.ReviewResponseDto;
import com.foodie.review.dto.response.ReviewSummaryDto;
import com.foodie.review.entity.Review;
import com.foodie.review.mapper.ReviewMapper;
import com.foodie.review.repository.ReviewRepository;
import com.foodie.review.repository.ReviewSpecification;
import com.foodie.review.service.ReviewModerationStore;
import com.foodie.review.service.ReviewService;
import com.foodie.shared.contract.CustomerSummaryProvider;
import com.foodie.shared.contract.OrderReviewQuery;
import com.foodie.shared.event.ReviewSubmittedEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderReviewQuery orderReviewQuery;
    private final CustomerSummaryProvider customerSummaryProvider;
    private final ReviewModerationStore moderationStore;
    private final ApplicationEventPublisher eventPublisher;

    public ReviewServiceImpl(
            ReviewRepository reviewRepository,
            OrderReviewQuery orderReviewQuery,
            CustomerSummaryProvider customerSummaryProvider,
            ReviewModerationStore moderationStore,
            ApplicationEventPublisher eventPublisher
    ) {
        this.reviewRepository = reviewRepository;
        this.orderReviewQuery = orderReviewQuery;
        this.customerSummaryProvider = customerSummaryProvider;
        this.moderationStore = moderationStore;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public ReviewResponseDto submit(UUID userCredentialId, UUID orderId, SubmitReviewRequestDto request) {
        UUID customerId = customerSummaryProvider.findByUserCredentialId(userCredentialId)
                .map(CustomerSummaryProvider.CustomerSummary::customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found."));

        OrderReviewQuery.OrderReviewSnapshot order = orderReviewQuery.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));

        if (!customerId.equals(order.customerId())) {
            // Hide existence of others' orders
            throw new ResourceNotFoundException("Order not found.");
        }
        if (order.status() != OrderStatus.DELIVERED) {
            throw new UnprocessableEntityException(
                    ErrorCode.ORDER_NOT_DELIVERED,
                    "Reviews are allowed only for delivered orders."
            );
        }
        if (reviewRepository.existsByOrderId(orderId)) {
            throw new ConflictException(
                    ErrorCode.REVIEW_ALREADY_EXISTS,
                    "A review already exists for this order."
            );
        }

        Review review = Review.submit(
                order.orderId(),
                customerId,
                order.restaurantId(),
                order.deliveryPartnerId(),
                request.restaurantRating(),
                request.deliveryRating(),
                request.comment()
        );

        try {
            review = reviewRepository.save(review);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(
                    ErrorCode.REVIEW_ALREADY_EXISTS,
                    "A review already exists for this order."
            );
        }

        eventPublisher.publishEvent(ReviewSubmittedEvent.of(
                review.getId(),
                review.getOrderId(),
                review.getRestaurantId(),
                review.getCustomerId(),
                review.getDeliveryPartnerId(),
                review.getRestaurantRating(),
                review.getDeliveryRating() == null ? null : review.getDeliveryRating().intValue()
        ));

        return ReviewMapper.toResponse(review);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RestaurantReviewItemDto> listForRestaurant(
            UUID restaurantId,
            int page,
            int size,
            String sort,
            List<Integer> rating,
            String search,
            Instant from,
            Instant to
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), resolveSort(sort));

        List<UUID> matchingCustomerIds = null;
        List<UUID> matchingOrderIds = null;

        if (search != null && !search.isBlank()) {
            matchingCustomerIds = customerSummaryProvider.findCustomerIdsByNameContaining(search);
            matchingOrderIds = orderReviewQuery.findOrderIdsBySearchText(search);
        }

        Specification<Review> spec = ReviewSpecification.filterReviews(
                restaurantId,
                rating,
                from,
                to,
                search,
                matchingCustomerIds,
                matchingOrderIds
        );

        Page<Review> result = reviewRepository.findAll(spec, pageable);

        List<Review> nonFlaggedReviews = result.getContent().stream()
                .filter(review -> !moderationStore.isFlagged(review.getId()))
                .toList();

        Set<UUID> customerIds = nonFlaggedReviews.stream().map(Review::getCustomerId).collect(Collectors.toSet());
        Set<UUID> orderIds = nonFlaggedReviews.stream().map(Review::getOrderId).collect(Collectors.toSet());

        Map<UUID, CustomerSummaryProvider.CustomerSummary> customersMap = customerSummaryProvider.findByCustomerIdIn(customerIds);
        Map<UUID, OrderReviewQuery.OrderDetailsSnapshot> orderDetailsMap = orderReviewQuery.findOrderDetailsByOrderIds(orderIds);

        List<RestaurantReviewItemDto> items = nonFlaggedReviews.stream().map(review -> {
            var customer = customersMap.get(review.getCustomerId());
            var orderDetails = orderDetailsMap.get(review.getOrderId());
            String customerName = customer != null ? customer.fullName() : "Verified Customer";
            String orderNumber = orderDetails != null ? orderDetails.orderNumber() : null;
            List<String> orderedItems = orderDetails != null ? orderDetails.itemNames() : List.of();
            return ReviewMapper.toPublicItem(review, customerName, orderNumber, orderedItems);
        }).toList();

        return new PageResult<>(items, new PaginationMeta(
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewSummaryDto getSummary(UUID restaurantId) {
        long totalReviews = reviewRepository.countByRestaurantId(restaurantId);
        Double rawAvg = reviewRepository.averageRestaurantRating(restaurantId);
        double averageRating = rawAvg != null
                ? BigDecimal.valueOf(rawAvg).setScale(1, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        long positiveReviews = reviewRepository.countByRestaurantIdAndRestaurantRatingGreaterThanEqual(restaurantId, (short) 4);
        long needsImprovement = reviewRepository.countByRestaurantIdAndRestaurantRatingLessThan(restaurantId, (short) 4);

        long star1 = reviewRepository.countByRestaurantIdAndRestaurantRating(restaurantId, (short) 1);
        long star2 = reviewRepository.countByRestaurantIdAndRestaurantRating(restaurantId, (short) 2);
        long star3 = reviewRepository.countByRestaurantIdAndRestaurantRating(restaurantId, (short) 3);
        long star4 = reviewRepository.countByRestaurantIdAndRestaurantRating(restaurantId, (short) 4);
        long star5 = reviewRepository.countByRestaurantIdAndRestaurantRating(restaurantId, (short) 5);

        Map<String, Long> starCounts = new LinkedHashMap<>();
        starCounts.put("1", star1);
        starCounts.put("2", star2);
        starCounts.put("3", star3);
        starCounts.put("4", star4);
        starCounts.put("5", star5);

        return new ReviewSummaryDto(
                averageRating,
                totalReviews,
                positiveReviews,
                needsImprovement,
                starCounts
        );
    }

    @Override
    public void flagForModeration(UUID reviewId, String reason) {
        if (!reviewRepository.existsById(reviewId)) {
            throw new ResourceNotFoundException("Review not found.");
        }
        moderationStore.flag(reviewId, reason);
    }

    @Override
    public void clearModerationFlag(UUID reviewId) {
        moderationStore.clear(reviewId);
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private static Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String cleaned = sort.trim();
        if ("newest".equalsIgnoreCase(cleaned) || "createdAt".equals(cleaned) || "-createdAt".equals(cleaned) || "createdAt,desc".equalsIgnoreCase(cleaned)) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        if ("oldest".equalsIgnoreCase(cleaned) || "+createdAt".equals(cleaned) || "createdAt,asc".equalsIgnoreCase(cleaned)) {
            return Sort.by(Sort.Direction.ASC, "createdAt");
        }
        if ("highest".equalsIgnoreCase(cleaned) || "restaurantRating".equals(cleaned) || "-restaurantRating".equals(cleaned) || "restaurantRating,desc".equalsIgnoreCase(cleaned)) {
            return Sort.by(Sort.Direction.DESC, "restaurantRating");
        }
        if ("lowest".equalsIgnoreCase(cleaned) || "+restaurantRating".equals(cleaned) || "restaurantRating,asc".equalsIgnoreCase(cleaned)) {
            return Sort.by(Sort.Direction.ASC, "restaurantRating");
        }
        throw new BadRequestException(
                ErrorCode.INVALID_SORT_FIELD, "Allowed sort fields: createdAt, restaurantRating, newest, oldest, highest, lowest.");
    }
}
