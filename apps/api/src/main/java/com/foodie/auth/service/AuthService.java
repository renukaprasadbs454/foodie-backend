package com.foodie.auth.service;

import com.foodie.auth.dto.request.AdminLoginRequestDto;
import com.foodie.auth.dto.request.CustomerLoginRequestDto;
import com.foodie.auth.dto.request.CustomerRegisterRequestDto;
import com.foodie.auth.dto.request.ForgotPasswordRequestDto;
import com.foodie.auth.dto.request.GoogleAuthRequestDto;
import com.foodie.auth.dto.request.RequestOtpRequestDto;
import com.foodie.auth.dto.request.ResetPasswordRequestDto;
import com.foodie.auth.dto.request.VerifyOtpRequestDto;
import com.foodie.auth.dto.response.TokenPairResponseDto;
import java.util.UUID;

/**
 * Auth module public contract.
 */
public interface AuthService {

    void requestOtp(RequestOtpRequestDto request);

    void requestOtp(String phoneNumber);

    TokenPairResponseDto verifyOtp(VerifyOtpRequestDto request);

    TokenPairResponseDto authenticateWithGoogle(GoogleAuthRequestDto request);

    TokenPairResponseDto registerCustomer(CustomerRegisterRequestDto request);

    TokenPairResponseDto loginCustomer(CustomerLoginRequestDto request);

    void forgotPassword(ForgotPasswordRequestDto request);

    void resetPassword(ResetPasswordRequestDto request);

    /** Admin email/password login. Reuses JWT + refresh token issuance. */
    TokenPairResponseDto loginAdmin(AdminLoginRequestDto request);

    TokenPairResponseDto refresh(String refreshToken);

    void revoke(String refreshToken);

    void revokeAllForUser(UUID userCredentialId);
}
