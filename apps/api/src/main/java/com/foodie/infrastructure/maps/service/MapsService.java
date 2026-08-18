package com.foodie.infrastructure.maps.service;

import com.foodie.infrastructure.maps.dto.response.GeocodeResponseDto;
import com.foodie.infrastructure.maps.dto.response.ReverseGeocodeResponseDto;
import java.math.BigDecimal;

public interface MapsService {

    GeocodeResponseDto geocode(String address);

    ReverseGeocodeResponseDto reverseGeocode(BigDecimal latitude, BigDecimal longitude);

    boolean isValidCoordinate(BigDecimal latitude, BigDecimal longitude);

    double calculateDistanceKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2);
}
