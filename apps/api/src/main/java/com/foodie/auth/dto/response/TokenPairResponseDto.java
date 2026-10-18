package com.foodie.auth.dto.response;

import com.foodie.common.enums.UserType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record TokenPairResponseDto(
        String accessToken,
        String refreshToken,
        @Schema(example = "900") long expiresIn,
        UserType userType,
        boolean isNewUser,
        /** user_credential.id (JWT subject). */
        UUID userId,
        /**
         * Admin role name when {@code userType=ADMIN}; null for other user types.
         * Binding values: SUPER_ADMIN, OPS, FINANCE, SUPPORT.
         */
        String role,
        String assignedZoneId,
        String assignedZoneName
) {
    public TokenPairResponseDto(
            String accessToken,
            String refreshToken,
            long expiresIn,
            UserType userType,
            boolean isNewUser,
            UUID userId,
            String role) {
        this(accessToken, refreshToken, expiresIn, userType, isNewUser, userId, role, null, null);
    }
}
