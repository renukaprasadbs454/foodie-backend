package com.foodie.menu.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MenuItemResponseDto(
        UUID menuItemId,
        UUID categoryId,
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
        @JsonProperty("avg_rating") BigDecimal avgRating,
        @JsonProperty("review_count") Long reviewCount) {

    public MenuItemResponseDto(
            UUID menuItemId,
            UUID categoryId,
            String name,
            String description,
            BigDecimal basePrice,
            boolean isVeg,
            String foodType,
            boolean isAvailable,
            String imageUrl,
            String packageSize,
            String preparationTime,
            BigDecimal gstPct) {
        this(
                menuItemId,
                categoryId,
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
                BigDecimal.ZERO,
                0L);
    }

    public MenuItemResponseDto(
            UUID menuItemId,
            UUID categoryId,
            String name,
            String description,
            BigDecimal basePrice,
            boolean isVeg,
            boolean isAvailable,
            String imageUrl) {
        this(
                menuItemId,
                categoryId,
                name,
                description,
                basePrice,
                isVeg,
                isVeg ? "VEG" : "NON_VEG",
                isAvailable,
                imageUrl,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0L);
    }

    @JsonProperty("averageRating")
    public BigDecimal getAverageRating() {
        return avgRating;
    }

    @JsonProperty("average_rating")
    public BigDecimal getAverageRatingSnake() {
        return avgRating;
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

