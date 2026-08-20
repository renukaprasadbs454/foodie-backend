package com.foodie.review.repository;

import com.foodie.review.entity.Review;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class ReviewSpecification {

    private ReviewSpecification() {
    }

    public static Specification<Review> filterReviews(
            UUID restaurantId,
            List<Integer> ratings,
            Instant from,
            Instant to,
            String search,
            Collection<UUID> matchingCustomerIds,
            Collection<UUID> matchingOrderIds
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (restaurantId != null) {
                predicates.add(cb.equal(root.get("restaurantId"), restaurantId));
            }

            if (ratings != null && !ratings.isEmpty()) {
                if (ratings.size() == 1) {
                    predicates.add(cb.equal(root.get("restaurantRating"), ratings.get(0).shortValue()));
                } else {
                    List<Short> shortRatings = ratings.stream().map(Integer::shortValue).toList();
                    predicates.add(root.get("restaurantRating").in(shortRatings));
                }
            }

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }

            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            if (search != null && !search.isBlank()) {
                String likePattern = "%" + search.trim().toLowerCase() + "%";
                Predicate commentLike = cb.like(cb.lower(root.get("comment")), likePattern);

                List<Predicate> searchPredicates = new ArrayList<>();
                searchPredicates.add(commentLike);

                if (matchingCustomerIds != null && !matchingCustomerIds.isEmpty()) {
                    searchPredicates.add(root.get("customerId").in(matchingCustomerIds));
                }

                if (matchingOrderIds != null && !matchingOrderIds.isEmpty()) {
                    searchPredicates.add(root.get("orderId").in(matchingOrderIds));
                }

                predicates.add(cb.or(searchPredicates.toArray(new Predicate[0])));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
