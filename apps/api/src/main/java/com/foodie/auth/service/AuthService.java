package com.foodie.auth.service;

import com.foodie.auth.dto.request.GoogleAuthRequestDto;
import com.foodie.auth.dto.request.VerifyOtpRequestDto;
import com.foodie.auth.dto.response.TokenPairResponseDto;
import java.util.UUID;

/**
 * Auth module public contract (Phase3 §2.1).
 * requestOtp returns void — HTTP contract returns data:null (API Contracts 1.1).
 */
public interface AuthService {

    void requestOtp(String phoneNumber);

    TokenPairResponseDto verifyOtp(VerifyOtpRequestDto request);

    TokenPairResponseDto authenticateWithGoogle(GoogleAuthRequestDto request);

    TokenPairResponseDto refresh(String refreshToken);

    void revoke(String refreshToken);

    void revokeAllForUser(UUID userCredentialId);
}
