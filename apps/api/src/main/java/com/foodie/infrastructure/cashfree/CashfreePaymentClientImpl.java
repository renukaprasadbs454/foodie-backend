package com.foodie.infrastructure.cashfree;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.common.exception.ExternalServiceException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CashfreePaymentClientImpl implements CashfreePaymentClient {

    private static final Logger log = LoggerFactory.getLogger(CashfreePaymentClientImpl.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private String clean(String val) {
        if (val == null) return "";
        val = val.trim();
        if (val.startsWith("\"") && val.endsWith("\"")) val = val.substring(1, val.length() - 1);
        if (val.startsWith("'") && val.endsWith("'")) val = val.substring(1, val.length() - 1);
        return val.trim();
    }

    public CashfreePaymentClientImpl(
            @Value("${CASHFREE_APP_ID:}") String appId,
            @Value("${CASHFREE_SECRET_KEY:}") String secretKey,
            @Value("${CASHFREE_ENV:SANDBOX}") String env,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        String realAppId = clean(appId);
        String realSecretKey = clean(secretKey);
        if (realAppId.startsWith("cfsk_")) {
            String temp = realAppId;
            realAppId = realSecretKey;
            realSecretKey = temp;
            log.warn("Detecting swapped Cashfree keys in environment, auto-correcting.");
        }

        String baseUrl = env != null && clean(env).equalsIgnoreCase("PRODUCTION") 
                ? "https://api.cashfree.com/pg" 
                : "https://sandbox.cashfree.com/pg";
                
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-client-id", realAppId)
                .defaultHeader("x-client-secret", realSecretKey)
                .defaultHeader("x-api-version", "2023-08-01")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Override
    public CashfreeOrderCreateResult createOrder(BigDecimal amount, String customerId, String customerPhone, String notesOrderId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("order_id", notesOrderId != null ? notesOrderId : ("OD_" + System.currentTimeMillis()));
        body.put("order_amount", amount.setScale(2, RoundingMode.HALF_UP));
        body.put("order_currency", "INR");
        body.put("customer_details", Map.of(
            "customer_id", customerId,
            "customer_phone", customerPhone != null && !customerPhone.isBlank() ? customerPhone : "9999999999"
        ));
        body.put("order_meta", Map.of(
            "return_url", "https://api.foodie.kwiko.org/api/v1/payments/cashfree/return?order_id={order_id}"
        ));

        JsonNode node = post("/orders", body);
        String paymentSessionId = node.path("payment_session_id").asText(null);
        String cfOrderId = node.path("order_id").asText(null);
        if (paymentSessionId == null || cfOrderId == null) {
            throw new ExternalServiceException("Cashfree create order missing session id or order_id.");
        }
        return new CashfreeOrderCreateResult(paymentSessionId, cfOrderId);
    }

    @Override
    public CashfreeOrderFetchResult fetchOrder(String cashfreeOrderId) {
        try {
            String json = restClient.get()
                    .uri("/orders/" + cashfreeOrderId)
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(json == null ? "{}" : json);
            String status = node.path("order_status").asText("");
            return new CashfreeOrderFetchResult(status);
        } catch (Exception ex) {
            log.error("Failed to fetch Cashfree order status", ex);
            throw new ExternalServiceException("Cashfree order fetch failed");
        }
    }

    @Override
    public CashfreeRefundResult createRefund(String cashfreeOrderId, BigDecimal amount, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("refund_amount", amount.setScale(2, RoundingMode.HALF_UP));
        body.put("refund_id", "REF_" + System.currentTimeMillis());
        body.put("refund_note", reason == null ? "" : reason);
        JsonNode node = post("/orders/" + cashfreeOrderId + "/refunds", body);
        String cfRefundId = node.path("cf_refund_id").asText(null);
        if (cfRefundId == null || cfRefundId.isBlank()) {
            throw new ExternalServiceException("Cashfree create refund failed.");
        }
        return new CashfreeRefundResult(cfRefundId);
    }

    private JsonNode post(String path, Object body) {
        try {
            String json = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            String responseBody = ex.getResponseBodyAsString();
            log.error("Cashfree HTTP {} on {}: {}", status, path, responseBody);
            throw new com.foodie.common.exception.BadRequestException(com.foodie.common.exception.ErrorCode.BAD_REQUEST, "CF Rejected: " + responseBody);
        } catch (Exception ex) {
            log.error("Cashfree call failed on {}", path, ex);
            throw new com.foodie.common.exception.BadRequestException(com.foodie.common.exception.ErrorCode.BAD_REQUEST, "CF Timeout: " + ex.getMessage());
        }
    }
}
