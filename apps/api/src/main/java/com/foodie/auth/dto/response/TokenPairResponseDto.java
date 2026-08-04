package com.foodie.auth.dto.response;

import com.foodie.common.enums.UserType;
import io.swagger.v3.oas.annotations.media.Schema;

public record TokenPairResponseDto(
        String accessToken,
        String refreshToken,
        @Schema(example = "900") long expiresIn,
        UserType userType,
        boolean isNewUser
) {
}
