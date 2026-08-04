package com.foodie.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.foodie.support.AbstractIntegrationTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class AuthModuleIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CapturingSmsSender capturingSmsSender;

    @Test
    void otpLogin_refresh_and_logout_flow() {
        String phone = "+919876543210";

        ResponseEntity<Map> requestOtp = restTemplate.postForEntity(
                "/api/v1/auth/otp/request",
                Map.of("phoneNumber", phone),
                Map.class
        );
        assertThat(requestOtp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(requestOtp.getBody()).containsEntry("success", true);

        await().atMost(Duration.ofSeconds(3)).until(() -> capturingSmsSender.lastOtp(phone) != null);
        String otp = capturingSmsSender.lastOtp(phone);

        ResponseEntity<Map> verify = restTemplate.postForEntity(
                "/api/v1/auth/otp/verify",
                Map.of(
                        "phoneNumber", phone,
                        "otp", otp,
                        "userType", "CUSTOMER",
                        "deviceInfo", "test-device"
                ),
                Map.class
        );
        assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) verify.getBody().get("data");
        assertThat(data.get("accessToken")).isNotNull();
        assertThat(data.get("refreshToken")).isNotNull();
        assertThat(data.get("userType")).isEqualTo("CUSTOMER");
        assertThat(data.get("isNewUser")).isEqualTo(true);
        assertThat(data.get("expiresIn")).isEqualTo(900);

        String refreshToken = data.get("refreshToken").toString();
        ResponseEntity<Map> refreshed = restTemplate.postForEntity(
                "/api/v1/auth/refresh",
                Map.of("refreshToken", refreshToken),
                Map.class
        );
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> refreshedData = (Map<?, ?>) refreshed.getBody().get("data");
        String newAccess = refreshedData.get("accessToken").toString();
        String newRefresh = refreshedData.get("refreshToken").toString();
        assertThat(newRefresh).isNotEqualTo(refreshToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(newAccess);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> logout = restTemplate.exchange(
                "/api/v1/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("refreshToken", newRefresh), headers),
                Map.class
        );
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> reuse = restTemplate.postForEntity(
                "/api/v1/auth/refresh",
                Map.of("refreshToken", refreshToken),
                Map.class
        );
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void requestOtp_invalidPhone_returnsValidationFailed() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/otp/request",
                Map.of("phoneNumber", "9876543210"),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("success")).isEqualTo(false);
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("VALIDATION_FAILED");
    }
}
