package com.foodie.auth.dto.request;

import com.foodie.auth.enums.OtpPurpose;
import com.foodie.auth.enums.OtpUserType;
import com.foodie.common.validation.ValidPhoneNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RequestOtpRequestDto(
        @NotBlank
        @ValidPhoneNumber
        @Schema(example = "+919876543210")
        String phoneNumber,

        @NotNull
        @Schema(example = "CUSTOMER", allowableValues = {"CUSTOMER", "RESTAURANT", "RESTAURANT_ADMIN", "DELIVERY_PARTNER", "ADMIN"})
        OtpUserType userType,

        @Schema(example = "REGISTRATION", allowableValues = {"REGISTRATION", "LOGIN", "PHONE_VERIFICATION", "PASSWORD_RESET"})
        OtpPurpose purpose
) {
    public RequestOtpRequestDto(String phoneNumber, OtpUserType userType) {
        this(phoneNumber, userType, OtpPurpose.REGISTRATION);
    }

    public RequestOtpRequestDto(String phoneNumber) {
        this(phoneNumber, OtpUserType.CUSTOMER, OtpPurpose.REGISTRATION);
    }
}
