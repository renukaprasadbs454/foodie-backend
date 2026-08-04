package com.foodie.auth.controller;

import com.foodie.auth.dto.request.GoogleAuthRequestDto;
import com.foodie.auth.dto.request.LogoutRequestDto;
import com.foodie.auth.dto.request.RefreshTokenRequestDto;
import com.foodie.auth.dto.request.RequestOtpRequestDto;
import com.foodie.auth.dto.request.VerifyOtpRequestDto;
import com.foodie.auth.dto.response.TokenPairResponseDto;
import com.foodie.auth.service.AuthService;
import com.foodie.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/otp/request")
    @Operation(summary = "Request OTP", description = "Generate and dispatch a one-time password via SMS.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP dispatched"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid phone"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limited")
    })
    public ResponseEntity<ApiResponse<Void>> requestOtp(@Valid @RequestBody RequestOtpRequestDto request) {
        authService.requestOtp(request.phoneNumber());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify OTP and authenticate")
    public ResponseEntity<ApiResponse<TokenPairResponseDto>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.success(authService.verifyOtp(request)));
    }

    @PostMapping("/google")
    @Operation(summary = "Authenticate Customer via Google ID token")
    public ResponseEntity<ApiResponse<TokenPairResponseDto>> google(
            @Valid @RequestBody GoogleAuthRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.success(authService.authenticateWithGoogle(request)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token")
    public ResponseEntity<ApiResponse<TokenPairResponseDto>> refresh(
            @Valid @RequestBody RefreshTokenRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(request.refreshToken())));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke refresh token (single-device logout)")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequestDto request) {
        authService.revoke(request.refreshToken());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.success(null));
    }
}
