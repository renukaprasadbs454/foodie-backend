package com.foodie.review;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.common.dto.PaginationMeta;
import com.foodie.common.exception.GlobalExceptionHandler;
import com.foodie.review.controller.ReviewController;
import com.foodie.review.dto.response.RestaurantReviewItemDto;
import com.foodie.review.dto.response.ReviewSummaryDto;
import com.foodie.review.service.ReviewService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    @Mock
    private ReviewService reviewService;

    private MockMvc mockMvc;

    private final UUID restaurantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReviewController controller = new ReviewController(reviewService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listForRestaurant_validRestaurantId_returns200() throws Exception {
        UUID reviewId = UUID.randomUUID();
        RestaurantReviewItemDto item = new RestaurantReviewItemDto(
                reviewId,
                reviewId,
                "John Doe",
                true,
                "ORD-001",
                "Order ORD-001",
                List.of("Pizza", "Coke"),
                "Pizza, Coke",
                5,
                4,
                "Delicious food!",
                Instant.now()
        );
        ReviewService.PageResult<RestaurantReviewItemDto> pageResult = new ReviewService.PageResult<>(
                List.of(item),
                new PaginationMeta(0, 20, 1L, 1)
        );

        when(reviewService.listForRestaurant(eq(restaurantId), anyInt(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/restaurants/{id}/reviews", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].comment").value("Delicious food!"))
                .andExpect(jsonPath("$.data[0].customerName").value("John Doe"))
                .andExpect(jsonPath("$.meta.pagination.totalElements").value(1));
    }

    @Test
    void getSummary_validRestaurantId_returns200() throws Exception {
        ReviewSummaryDto summary = new ReviewSummaryDto(
                4.8,
                10L,
                8L,
                2L,
                Map.of("1", 1L, "2", 0L, "3", 1L, "4", 3L, "5", 5L)
        );

        when(reviewService.getSummary(restaurantId)).thenReturn(summary);

        mockMvc.perform(get("/api/v1/restaurants/{id}/reviews/summary", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.averageRating").value(4.8))
                .andExpect(jsonPath("$.data.totalReviews").value(10))
                .andExpect(jsonPath("$.data.positiveReviews").value(8))
                .andExpect(jsonPath("$.data.needsImprovement").value(2))
                .andExpect(jsonPath("$.data.starCounts['5']").value(5));
    }
}
