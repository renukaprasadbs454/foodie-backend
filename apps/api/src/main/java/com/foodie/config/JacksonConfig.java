package com.foodie.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalTime;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Global ObjectMapper customization - ISO-8601 dates, no timestamps-as-numbers, flexible LocalTime parsing.
 */
@Configuration
public class JacksonConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addDeserializer(LocalTime.class, new FlexibleLocalTimeDeserializer());

        return builder -> builder
                .modules(javaTimeModule)
                .deserializers(new FlexibleLocalTimeDeserializer())
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}

