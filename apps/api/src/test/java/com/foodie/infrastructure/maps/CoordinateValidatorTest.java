package com.foodie.infrastructure.maps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodie.common.exception.BadRequestException;
import com.foodie.infrastructure.maps.util.CoordinateValidator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CoordinateValidatorTest {

    @Test
    void isValid_validCoordinates_returnsTrue() {
        assertThat(CoordinateValidator.isValid(new BigDecimal("12.9716"), new BigDecimal("77.5946"))).isTrue();
        assertThat(CoordinateValidator.isValid(new BigDecimal("-90.0"), new BigDecimal("-180.0"))).isTrue();
        assertThat(CoordinateValidator.isValid(new BigDecimal("90.0"), new BigDecimal("180.0"))).isTrue();
    }

    @Test
    void isValid_nullOrInvalid_returnsFalse() {
        assertThat(CoordinateValidator.isValid(null, new BigDecimal("77.5946"))).isFalse();
        assertThat(CoordinateValidator.isValid(new BigDecimal("12.9716"), null)).isFalse();
        assertThat(CoordinateValidator.isValid(new BigDecimal("91.0"), new BigDecimal("77.5946"))).isFalse();
        assertThat(CoordinateValidator.isValid(new BigDecimal("12.9716"), new BigDecimal("181.0"))).isFalse();
        assertThat(CoordinateValidator.isValid(new BigDecimal("-91.0"), new BigDecimal("77.5946"))).isFalse();
        assertThat(CoordinateValidator.isValid(new BigDecimal("12.9716"), new BigDecimal("-181.0"))).isFalse();
    }

    @Test
    void validate_validCoordinates_doesNotThrow() {
        CoordinateValidator.validate(new BigDecimal("12.9716"), new BigDecimal("77.5946"));
    }

    @Test
    void validate_nullLatitude_throwsBadRequest() {
        assertThatThrownBy(() -> CoordinateValidator.validate(null, new BigDecimal("77.5946")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("null");
    }

    @Test
    void validate_outOfBoundsLatitude_throwsBadRequest() {
        assertThatThrownBy(() -> CoordinateValidator.validate(new BigDecimal("95.0000"), new BigDecimal("77.5946")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Latitude must be between -90 and 90");
    }

    @Test
    void validate_outOfBoundsLongitude_throwsBadRequest() {
        assertThatThrownBy(() -> CoordinateValidator.validate(new BigDecimal("12.9716"), new BigDecimal("-200.0000")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Longitude must be between -180 and 180");
    }
}
