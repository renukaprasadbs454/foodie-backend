package com.foodie.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.foodie.auth.CapturingSmsSender;
import com.foodie.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class RestaurantModuleIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CapturingSmsSender capturingSmsSender;

    @Autowired
    private com.foodie.restaurant.service.RestaurantService restaurantService;

    @Test
    void register_pendingHiddenFromPublic_visibleAfterApprove() {
        String accessToken = signup("+919822233344", "RESTAURANT");
        HttpHeaders auth = bearer(accessToken);

        ResponseEntity<Map> created = restTemplate.exchange(
                "/api/v1/restaurants",
                HttpMethod.POST,
                new HttpEntity<>(createBody(), auth),
                Map.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> data = (Map<?, ?>) created.getBody().get("data");
        assertThat(data.get("status")).isEqualTo("PENDING");
        assertThat(new BigDecimal(data.get("commissionPct").toString())).isEqualByComparingTo("18.00");
        UUID restaurantId = UUID.fromString(data.get("restaurantId").toString());

        ResponseEntity<Map> publicGet = restTemplate.getForEntity(
                "/api/v1/restaurants/" + restaurantId, Map.class);
        assertThat(publicGet.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> ownerGet = restTemplate.exchange(
                "/api/v1/restaurants/" + restaurantId,
                HttpMethod.GET,
                new HttpEntity<>(auth),
                Map.class
        );
        assertThat(ownerGet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) ownerGet.getBody().get("data")).get("status")).isEqualTo("PENDING");

        restaurantService.approve(restaurantId, UUID.randomUUID());

        ResponseEntity<Map> publicAfter = restTemplate.getForEntity(
                "/api/v1/restaurants/" + restaurantId, Map.class);
        assertThat(publicAfter.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> publicData = (Map<?, ?>) publicAfter.getBody().get("data");
        assertThat(publicData.get("status")).isEqualTo("APPROVED");
        assertThat(publicData.containsKey("commissionPct")).isFalse();
        assertThat(publicData.containsKey("ownerUserCredentialId")).isFalse();

        ResponseEntity<Map> list = restTemplate.getForEntity("/api/v1/restaurants?search=Spice", Map.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) list.getBody().get("data")).isNotEmpty();
        assertThat(list.getBody().get("meta")).isNotNull();
    }

    private Map<String, Object> createBody() {
        return Map.of(
                "name", "Spice Route Kitchen",
                "description", "Authentic South Indian cuisine",
                "cuisineTypes", List.of("SOUTH_INDIAN", "VEGETARIAN"),
                "address", Map.of(
                        "line1", "Flat 402",
                        "city", "Bengaluru",
                        "pincode", "560103",
                        "latitude", 12.9352,
                        "longitude", 77.6912
                ),
                "commissionPct", 99.0
        );
    }

    private String signup(String phone, String userType) {
        restTemplate.postForEntity("/api/v1/auth/otp/request", Map.of("phoneNumber", phone), Map.class);
        await().atMost(Duration.ofSeconds(3)).until(() -> capturingSmsSender.lastOtp(phone) != null);
        ResponseEntity<Map> verify = restTemplate.postForEntity(
                "/api/v1/auth/otp/verify",
                Map.of(
                        "phoneNumber", phone,
                        "otp", capturingSmsSender.lastOtp(phone),
                        "userType", userType,
                        "deviceInfo", "restaurant-it"
                ),
                Map.class
        );
        assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Map<?, ?>) verify.getBody().get("data")).get("accessToken").toString();
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
