package com.foodie.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodie.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class PhoneUtilsTest {

    @Test
    void normalize_validFormats_returnsE164Format() {
        assertThat(PhoneUtils.normalize("+919876543210")).isEqualTo("+919876543210");
        assertThat(PhoneUtils.normalize("919876543210")).isEqualTo("+919876543210");
        assertThat(PhoneUtils.normalize("9876543210")).isEqualTo("+919876543210");
        assertThat(PhoneUtils.normalize(" +91 987-654-3210 ")).isEqualTo("+919876543210");
    }

    @Test
    void normalize_invalidInput_throwsBadRequestException() {
        assertThatThrownBy(() -> PhoneUtils.normalize(null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PhoneUtils.normalize(""))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PhoneUtils.normalize("123"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void formatForWhatsApp_stripsLeadingPlus() {
        assertThat(PhoneUtils.formatForWhatsApp("+919876543210")).isEqualTo("919876543210");
        assertThat(PhoneUtils.formatForWhatsApp("9876543210")).isEqualTo("919876543210");
    }

    @Test
    void mask_masksMiddleDigits() {
        assertThat(PhoneUtils.mask("+919876543210")).isEqualTo("******3210");
        assertThat(PhoneUtils.mask("9876543210")).isEqualTo("******3210");
        assertThat(PhoneUtils.mask(null)).isEqualTo("****");
        assertThat(PhoneUtils.mask("12")).isEqualTo("****");
    }
}
