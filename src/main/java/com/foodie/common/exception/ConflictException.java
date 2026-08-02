package com.foodie.common.exception;

public class ConflictException extends BaseException {
    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
