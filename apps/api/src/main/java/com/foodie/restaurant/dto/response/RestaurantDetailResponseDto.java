package com.foodie.restaurant.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record RestaurantDetailResponseDto(
                UUID restaurantId,
                String name,
                String description,
                List<String> cuisineTypes,
                RestaurantAddressResponseDto address,
                BigDecimal latitude,
                BigDecimal longitude,
                String logoImageUrl,
                String coverImageUrl,
                BigDecimal avgRating,
                String status,
                com.foodie.common.enums.RestaurantType restaurantType,
                String rejectionReason,
                BigDecimal commissionPct,
                UUID ownerUserCredentialId,
                RestaurantLegalDetailResponseDto legalDetails,
                List<RestaurantDocumentResponseDto> documents,
                Boolean isOpen,
                Integer topPosition,
                String openTime,
                String closeTime,
                List<String> openDays,
                String phone,
                String contactPhone,
                Long reviewCount,
                String zoneId,
                String zoneName) {

        @JsonProperty("averageRating")
        public BigDecimal getAverageRating() {
            return avgRating;
        }

        @JsonProperty("average_rating")
        public BigDecimal getAverageRatingSnake() {
            return avgRating;
        }

        @JsonProperty("totalReviews")
        public Long getTotalReviews() {
            return reviewCount != null ? reviewCount : 0L;
        }

        @JsonProperty("review_count")
        public Long getReviewCountSnake() {
            return reviewCount != null ? reviewCount : 0L;
        }

        @JsonProperty("total_reviews")
        public Long getTotalReviewsSnake() {
            return reviewCount != null ? reviewCount : 0L;
        }

        @JsonProperty("zoneId")
        public String getEffectiveZoneId() {
            return zoneId != null ? zoneId : deriveZoneId(address, latitude, longitude);
        }

        @JsonProperty("zoneName")
        public String getEffectiveZoneName() {
            return zoneName != null ? zoneName : deriveZoneName(address, latitude, longitude);
        }

        public static String deriveZoneId(RestaurantAddressResponseDto addr, BigDecimal lat, BigDecimal lng) {
            if (addr != null && addr.city() != null) {
                String c = addr.city().trim().toLowerCase();
                if (c.contains("tumkur") || c.contains("tumakuru") || c.contains("tmk")) {
                    return "dz-tumkur";
                }
                if (c.contains("bangalore") || c.contains("bengaluru") || c.contains("blr")) {
                    return "dz-bangalore";
                }
            }
            if (addr != null && addr.formattedAddress() != null) {
                String fa = addr.formattedAddress().trim().toLowerCase();
                if (fa.contains("tumkur") || fa.contains("tumakuru")) {
                    return "dz-tumkur";
                }
                if (fa.contains("bangalore") || fa.contains("bengaluru")) {
                    return "dz-bangalore";
                }
            }
            if (lat != null && lng != null) {
                double dLat = lat.doubleValue();
                double dLng = lng.doubleValue();
                if (dLat > 13.2 && dLat < 13.5 && dLng > 77.0 && dLng < 77.3) {
                    return "dz-tumkur";
                }
            }
            return "dz-bangalore";
        }

        public static String deriveZoneName(RestaurantAddressResponseDto addr, BigDecimal lat, BigDecimal lng) {
            String zid = deriveZoneId(addr, lat, lng);
            return "dz-tumkur".equalsIgnoreCase(zid) ? "Tumkur Zone" : "Bangalore Zone";
        }

        public RestaurantDetailResponseDto(
                        UUID restaurantId,
                        String name,
                        String description,
                        List<String> cuisineTypes,
                        RestaurantAddressResponseDto address,
                        BigDecimal latitude,
                        BigDecimal longitude,
                        String logoImageUrl,
                        String coverImageUrl,
                        BigDecimal avgRating,
                        String status,
                        com.foodie.common.enums.RestaurantType restaurantType,
                        String rejectionReason,
                        BigDecimal commissionPct,
                        UUID ownerUserCredentialId,
                        RestaurantLegalDetailResponseDto legalDetails,
                        List<RestaurantDocumentResponseDto> documents,
                        Boolean isOpen,
                        Integer topPosition,
                        String openTime,
                        String closeTime,
                        List<String> openDays,
                        String phone,
                        String contactPhone,
                        Long reviewCount) {
                this(
                                restaurantId, name, description, cuisineTypes, address,
                                latitude, longitude, logoImageUrl, coverImageUrl, avgRating,
                                status, restaurantType, rejectionReason, commissionPct,
                                ownerUserCredentialId, legalDetails, documents, isOpen, topPosition,
                                openTime, closeTime, openDays, phone, contactPhone, reviewCount,
                                deriveZoneId(address, latitude, longitude),
                                deriveZoneName(address, latitude, longitude));
        }

        public RestaurantDetailResponseDto(
                        UUID restaurantId,
                        String name,
                        String description,
                        List<String> cuisineTypes,
                        RestaurantAddressResponseDto address,
                        BigDecimal latitude,
                        BigDecimal longitude,
                        String logoImageUrl,
                        String coverImageUrl,
                        BigDecimal avgRating,
                        String status,
                        com.foodie.common.enums.RestaurantType restaurantType,
                        String rejectionReason,
                        BigDecimal commissionPct,
                        UUID ownerUserCredentialId,
                        RestaurantLegalDetailResponseDto legalDetails,
                        List<RestaurantDocumentResponseDto> documents,
                        Boolean isOpen,
                        Integer topPosition,
                        String openTime,
                        String closeTime,
                        List<String> openDays,
                        String phone,
                        String contactPhone) {
                this(
                                restaurantId, name, description, cuisineTypes, address,
                                latitude, longitude, logoImageUrl, coverImageUrl, avgRating,
                                status, restaurantType, rejectionReason, commissionPct,
                                ownerUserCredentialId, legalDetails, documents, isOpen, topPosition,
                                openTime, closeTime, openDays, phone, contactPhone, 0L);
        }

        public RestaurantDetailResponseDto(
                        UUID restaurantId,
                        String name,
                        String description,
                        List<String> cuisineTypes,
                        RestaurantAddressResponseDto address,
                        BigDecimal latitude,
                        BigDecimal longitude,
                        String logoImageUrl,
                        String coverImageUrl,
                        BigDecimal avgRating,
                        String status,
                        BigDecimal commissionPct,
                        UUID ownerUserCredentialId,
                        String phone) {
                this(
                                restaurantId, name, description, cuisineTypes, address,
                                latitude, longitude, logoImageUrl, coverImageUrl, avgRating,
                                status, com.foodie.common.enums.RestaurantType.BOTH, null, commissionPct,
                                ownerUserCredentialId, null, null, Boolean.FALSE, null, null, null, null, phone, phone, 0L);
        }
}
