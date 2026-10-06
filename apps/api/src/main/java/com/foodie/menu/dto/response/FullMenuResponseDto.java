package com.foodie.menu.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FullMenuResponseDto(
        UUID restaurantId,
        List<MenuCategoryDto> categories) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MenuCategoryDto(
            UUID categoryId,
            String name,
            int displayOrder,
            List<MenuItemDto> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MenuItemDto(
            UUID menuItemId,
            String name,
            String description,
            BigDecimal basePrice,
            boolean isVeg,
            String foodType,
            boolean isAvailable,
            String imageUrl,
            String packageSize,
            String preparationTime,
            BigDecimal gstPct,
            List<VariantResponseDto> variants,
            @JsonProperty("avg_rating") BigDecimal avgRating,
            @JsonProperty("review_count") Long reviewCount) {

        public MenuItemDto(
                UUID menuItemId,
                String name,
                String description,
                BigDecimal basePrice,
                boolean isVeg,
                String foodType,
                boolean isAvailable,
                String imageUrl,
                String packageSize,
                String preparationTime,
                BigDecimal gstPct,
                List<VariantResponseDto> variants) {
            this(
                    menuItemId,
                    name,
                    description,
                    basePrice,
                    isVeg,
                    foodType,
                    isAvailable,
                    imageUrl,
                    packageSize,
                    preparationTime,
                    gstPct,
                    variants,
                    BigDecimal.ZERO,
                    0L);
        }

        @JsonProperty("avgRating")
        public BigDecimal getAvgRatingCamel() {
            return avgRating;
        }

        @JsonProperty("reviewCount")
        public Long getReviewCountCamel() {
            return reviewCount;
        }

        @JsonProperty("rating")
        public BigDecimal getRating() {
            return avgRating;
        }
    }
}

