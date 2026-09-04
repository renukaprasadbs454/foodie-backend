package com.foodie.admin.dto.request;

import jakarta.validation.constraints.Size;

public record RejectKycRequestDto(
        @Size(max = 500) String reason
) {
}
