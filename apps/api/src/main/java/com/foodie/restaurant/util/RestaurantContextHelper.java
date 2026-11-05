package com.foodie.restaurant.util;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class RestaurantContextHelper {

    private RestaurantContextHelper() {
    }

    public static Optional<UUID> getRequestedRestaurantId() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest req = servletAttrs.getRequest();
            String headerVal = req.getHeader("X-Restaurant-Id");
            if (headerVal != null && !headerVal.isBlank()) {
                try {
                    return Optional.of(UUID.fromString(headerVal.trim()));
                } catch (IllegalArgumentException ignored) {
                }
            }
            String paramVal = req.getParameter("restaurantId");
            if (paramVal != null && !paramVal.isBlank()) {
                try {
                    return Optional.of(UUID.fromString(paramVal.trim()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return Optional.empty();
    }
}

