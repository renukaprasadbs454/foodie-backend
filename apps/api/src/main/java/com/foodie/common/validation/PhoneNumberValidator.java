package com.foodie.common.validation;

import com.foodie.common.util.PhoneUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PhoneNumberValidator implements ConstraintValidator<ValidPhoneNumber, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            PhoneUtils.normalize(value);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
