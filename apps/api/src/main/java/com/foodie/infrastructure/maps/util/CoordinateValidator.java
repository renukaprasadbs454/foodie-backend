package com.foodie.infrastructure.maps.util;

import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.ErrorCode;
import java.math.BigDecimal;

public final class CoordinateValidator {

    private CoordinateValidator() {
    }

    public static boolean isValid(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }

        try {
            double lat = latitude.doubleValue();
            double lng = longitude.doubleValue();

            if (Double.isNaN(lat) || Double.isInfinite(lat) || Double.isNaN(lng) || Double.isInfinite(lng)) {
                return false;
            }

            return lat >= -90.0 && lat <= 90.0 && lng >= -180.0 && lng <= 180.0;
        } catch (NumberFormatException | ArithmeticException ex) {
            return false;
        }
    }

    public static void validate(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Latitude and longitude must not be null.");
        }

        try {
            double lat = latitude.doubleValue();
            double lng = longitude.doubleValue();

            if (Double.isNaN(lat) || Double.isInfinite(lat)) {
                throw new BadRequestException(ErrorCode.BAD_REQUEST, "Latitude must be a valid numeric value.");
            }
            if (Double.isNaN(lng) || Double.isInfinite(lng)) {
                throw new BadRequestException(ErrorCode.BAD_REQUEST, "Longitude must be a valid numeric value.");
            }

            if (lat < -90.0 || lat > 90.0) {
                throw new BadRequestException(ErrorCode.BAD_REQUEST, "Latitude must be between -90 and 90 degrees.");
            }
            if (lng < -180.0 || lng > 180.0) {
                throw new BadRequestException(ErrorCode.BAD_REQUEST, "Longitude must be between -180 and 180 degrees.");
            }
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Invalid coordinate numeric format.");
        }
    }
}
