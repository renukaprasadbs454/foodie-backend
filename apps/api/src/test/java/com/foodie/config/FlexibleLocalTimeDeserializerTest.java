package com.foodie.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.restaurant.dto.request.UpdateTimingsRequestDto;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class FlexibleLocalTimeDeserializerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        JacksonConfig config = new JacksonConfig();
        Jackson2ObjectMapperBuilderCustomizer customizer = config.jacksonCustomizer();
        customizer.customize(builder);
        objectMapper = builder.build();
    }

    @Test
    void deserialize_12HourFormatWithAmPm() throws Exception {
        String json = "{\"openTime\":\"12:00 AM\",\"closeTime\":\"11:30 PM\",\"openDays\":[\"MON\"]}";
        UpdateTimingsRequestDto dto = objectMapper.readValue(json, UpdateTimingsRequestDto.class);

        assertThat(dto.openTime()).isEqualTo(LocalTime.of(0, 0));
        assertThat(dto.closeTime()).isEqualTo(LocalTime.of(23, 30));
    }

    @Test
    void deserialize_12HourFormatMidday() throws Exception {
        String json = "{\"openTime\":\"12:00 PM\",\"closeTime\":\"01:30 PM\",\"openDays\":[\"MON\"]}";
        UpdateTimingsRequestDto dto = objectMapper.readValue(json, UpdateTimingsRequestDto.class);

        assertThat(dto.openTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(dto.closeTime()).isEqualTo(LocalTime.of(13, 30));
    }

    @Test
    void deserialize_24HourFormat() throws Exception {
        String json = "{\"openTime\":\"09:00\",\"closeTime\":\"22:00:00\",\"openDays\":[\"MON\"]}";
        UpdateTimingsRequestDto dto = objectMapper.readValue(json, UpdateTimingsRequestDto.class);

        assertThat(dto.openTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(dto.closeTime()).isEqualTo(LocalTime.of(22, 0));
    }
}

