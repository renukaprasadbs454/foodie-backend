package com.foodie.restaurant.mapper;

import com.foodie.restaurant.dto.response.RestaurantAddressResponseDto;
import com.foodie.restaurant.dto.response.RestaurantDetailResponseDto;
import com.foodie.restaurant.dto.response.RestaurantDocumentResponseDto;
import com.foodie.restaurant.dto.response.RestaurantSummaryResponseDto;
import com.foodie.restaurant.entity.Restaurant;
import com.foodie.restaurant.entity.RestaurantAddress;
import com.foodie.restaurant.entity.RestaurantDocument;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RestaurantMapper {

    public RestaurantSummaryResponseDto toSummary(Restaurant restaurant, String imageUrl) {
        return new RestaurantSummaryResponseDto(
                restaurant.getId(),
                restaurant.getName(),
                cuisineList(restaurant),
                restaurant.getAvgRating(),
                restaurant.getLatitude(),
                restaurant.getLongitude(),
                imageUrl
        );
    }

    public RestaurantDetailResponseDto toDetail(
            Restaurant restaurant,
            String logoUrl,
            String coverUrl,
            boolean privileged
    ) {
        RestaurantAddress address = restaurant.getAddress();
        return new RestaurantDetailResponseDto(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getDescription(),
                cuisineList(restaurant),
                new RestaurantAddressResponseDto(
                        address.getLine1(),
                        address.getLine2(),
                        address.getCity(),
                        address.getPincode(),
                        address.getLatitude(),
                        address.getLongitude()
                ),
                restaurant.getLatitude(),
                restaurant.getLongitude(),
                logoUrl,
                coverUrl,
                restaurant.getAvgRating(),
                restaurant.getStatus().name(),
                privileged ? restaurant.getCommissionPct() : null,
                privileged ? restaurant.getOwnerUserCredentialId() : null
        );
    }

    public RestaurantDocumentResponseDto toDocument(RestaurantDocument document) {
        return new RestaurantDocumentResponseDto(
                document.getId(),
                document.getDocType().name(),
                document.getVerifiedAt()
        );
    }

    private static List<String> cuisineList(Restaurant restaurant) {
        String[] types = restaurant.getCuisineTypes();
        return types == null ? List.of() : Arrays.asList(types);
    }
}
