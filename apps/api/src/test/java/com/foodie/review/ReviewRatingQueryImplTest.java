package com.foodie.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.foodie.review.repository.MenuItemRatingProjection;
import com.foodie.review.repository.ReviewRepository;
import com.foodie.review.service.impl.ReviewRatingQueryImpl;
import com.foodie.shared.contract.ReviewRatingQuery;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewRatingQueryImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    private ReviewRatingQueryImpl queryService;

    @BeforeEach
    void setUp() {
        queryService = new ReviewRatingQueryImpl(reviewRepository);
    }

    @Test
    void getMenuItemRating_withReviews() {
        UUID itemId = UUID.randomUUID();
        when(reviewRepository.averageMenuItemRating(itemId)).thenReturn(4.6666);
        when(reviewRepository.countMenuItemReviews(itemId)).thenReturn(3L);

        ReviewRatingQuery.MenuItemRating result = queryService.getMenuItemRating(itemId);

        assertThat(result.avgRating()).isEqualTo(new BigDecimal("4.7"));
        assertThat(result.reviewCount()).isEqualTo(3L);
    }

    @Test
    void getMenuItemRating_noReviews() {
        UUID itemId = UUID.randomUUID();
        when(reviewRepository.averageMenuItemRating(itemId)).thenReturn(0.0);
        when(reviewRepository.countMenuItemReviews(itemId)).thenReturn(0L);

        ReviewRatingQuery.MenuItemRating result = queryService.getMenuItemRating(itemId);

        assertThat(result.avgRating()).isEqualTo(BigDecimal.ZERO);
        assertThat(result.reviewCount()).isEqualTo(0L);
    }

    @Test
    void getMenuItemRatings_batch() {
        UUID item1 = UUID.randomUUID();
        UUID item2 = UUID.randomUUID();

        MenuItemRatingProjection proj1 = new MenuItemRatingProjection() {
            @Override
            public UUID getMenuItemId() {
                return item1;
            }

            @Override
            public Double getAvgRating() {
                return 4.25;
            }

            @Override
            public Long getReviewCount() {
                return 4L;
            }
        };

        when(reviewRepository.findRatingsByMenuItemIds(List.of(item1, item2)))
                .thenReturn(List.of(proj1));

        Map<UUID, ReviewRatingQuery.MenuItemRating> map = queryService.getMenuItemRatings(List.of(item1, item2));

        assertThat(map).hasSize(2);
        assertThat(map.get(item1).avgRating()).isEqualTo(new BigDecimal("4.3"));
        assertThat(map.get(item1).reviewCount()).isEqualTo(4L);
        assertThat(map.get(item2).avgRating()).isEqualTo(BigDecimal.ZERO);
        assertThat(map.get(item2).reviewCount()).isEqualTo(0L);
    }
}

