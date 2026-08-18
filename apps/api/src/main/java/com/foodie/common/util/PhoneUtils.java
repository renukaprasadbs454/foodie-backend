package com.foodie.common.util;

import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.ErrorCode;
import java.util.regex.Pattern;

public final class PhoneUtils {

    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private static final Pattern TEN_DIGIT_INDIAN = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern TWELVE_DIGIT_INDIAN = Pattern.compile("^91[6-9]\\d{9}$");

    private PhoneUtils() {
    }

    /**
     * Normalizes a phone number into standard E.164 format (+[country_code][number]).
     * Supports formats like: "+919876543210", "919876543210", "9876543210".
     */
    public static String normalize(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "Phone number is required.");
        }

        String cleaned = rawPhone.trim().replaceAll("[^+\\d]", "");
        if (cleaned.isEmpty()) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "Invalid phone number format.");
        }

        if (cleaned.startsWith("+")) {
            if (!E164_PATTERN.matcher(cleaned).matches()) {
                throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "Invalid E.164 phone number format.");
            }
            return cleaned;
        }

        // Handle raw 10-digit Indian mobile number
        if (TEN_DIGIT_INDIAN.matcher(cleaned).matches()) {
            return "+91" + cleaned;
        }

        // Handle raw 12-digit Indian mobile number without '+'
        if (TWELVE_DIGIT_INDIAN.matcher(cleaned).matches()) {
            return "+" + cleaned;
        }

        // Default fallback prepending '+'
        String formatted = "+" + cleaned;
        if (!E164_PATTERN.matcher(formatted).matches()) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "Invalid phone number format.");
        }
        return formatted;
    }

    /**
     * Strips leading '+' for APIs (like WhatsApp Cloud API) that require digits only.
     */
    public static String formatForWhatsApp(String phoneNumber) {
        String normalized = normalize(phoneNumber);
        return normalized.startsWith("+") ? normalized.substring(1) : normalized;
    }

    /**
     * Safely masks a phone number for logging and display without leaking PII.
     * Example: "+919876543210" -> "******3210"
     */
    public static String mask(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return "****";
        }
        String cleaned = phoneNumber.replaceAll("\\s+", "");
        if (cleaned.length() <= 4) {
            return "****";
        }
        return "******" + cleaned.substring(cleaned.length() - 4);
    }
}
