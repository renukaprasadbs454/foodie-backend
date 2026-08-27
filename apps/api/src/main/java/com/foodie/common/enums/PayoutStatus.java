package com.foodie.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PayoutStatus {
    REQUESTED,
    PROCESSING,
    COMPLETED,
    FAILED;

    @JsonCreator
    public static PayoutStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        switch (normalized) {
            case "COMPLETED":
            case "SUCCESS":
            case "SUCCESSFUL":
            case "PAID":
                return COMPLETED;
            case "FAILED":
            case "FAILURE":
            case "REJECTED":
                return FAILED;
            case "PROCESSING":
            case "PENDING":
            case "INITIATED":
                return PROCESSING;
            case "REQUESTED":
                return REQUESTED;
            default:
                try {
                    return PayoutStatus.valueOf(normalized);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Unknown PayoutStatus: " + value);
                }
        }
    }
}

