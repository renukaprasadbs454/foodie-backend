package com.foodie.delivery.dto.request;

import com.foodie.common.enums.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertDeliveryProfileRequestDto(
        @NotBlank
        @Size(max = 255)
        String fullName,

        @NotNull
        VehicleType vehicleType,

        @Size(max = 20)
        String vehicleNumber,

        @Size(max = 500)
        String addressLine1,

        @Size(max = 500)
        String addressLine2,

        @Size(max = 100)
        String city,

        @Size(max = 100)
        String state,

        @Size(max = 20)
        String pincode
) {
}
