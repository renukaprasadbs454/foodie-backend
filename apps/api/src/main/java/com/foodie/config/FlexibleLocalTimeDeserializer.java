package com.foodie.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;

/**
 * Flexible LocalTime deserializer accepting:
 * - 12-hour AM/PM formats: "12:00 AM", "12:00AM", "12:00:00 AM", "1:30 PM", etc.
 * - 24-hour formats: "00:00", "00:00:00", "14:30", "14:30:00", ISO-8601
 */
public class FlexibleLocalTimeDeserializer extends StdDeserializer<LocalTime> {

    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendOptional(DateTimeFormatter.ISO_LOCAL_TIME)
            .appendOptional(DateTimeFormatter.ofPattern("HH:mm:ss"))
            .appendOptional(DateTimeFormatter.ofPattern("H:mm:ss"))
            .appendOptional(DateTimeFormatter.ofPattern("HH:mm"))
            .appendOptional(DateTimeFormatter.ofPattern("H:mm"))
            .appendOptional(DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH))
            .appendOptional(DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH))
            .appendOptional(DateTimeFormatter.ofPattern("hh:mma", Locale.ENGLISH))
            .appendOptional(DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH))
            .appendOptional(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH))
            .appendOptional(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
            .toFormatter(Locale.ENGLISH);

    public FlexibleLocalTimeDeserializer() {
        super(LocalTime.class);
    }

    @Override
    public LocalTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String trimmed = text.trim();
        try {
            return LocalTime.parse(trimmed, FORMATTER);
        } catch (Exception ex) {
            String upper = trimmed.toUpperCase(Locale.ENGLISH).replaceAll("\\s+", " ");
            if (upper.endsWith("AM") || upper.endsWith("PM")) {
                boolean isPm = upper.endsWith("PM");
                String rawTime = upper.substring(0, upper.length() - 2).trim();
                String[] parts = rawTime.split(":");
                int hours = Integer.parseInt(parts[0]);
                int minutes = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                int seconds = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                if (isPm && hours < 12) {
                    hours += 12;
                } else if (!isPm && hours == 12) {
                    hours = 0;
                }
                return LocalTime.of(hours, minutes, seconds);
            }
            return LocalTime.parse(trimmed);
        }
    }
}

