package com.foodie.infrastructure.whatsapp;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.auth.enums.OtpPurpose;
import com.foodie.auth.enums.OtpUserType;
import com.foodie.common.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class WhatsAppCloudApiSenderTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private WhatsAppProperties properties;
    private WhatsAppCloudApiSender sender;

    @BeforeEach
    void setUp() {
        properties = new WhatsAppProperties();
        properties.setEnabled(true);
        properties.setAccessToken("test_access_token");
        properties.setPhoneNumberId("test_phone_number_id");
        properties.setBusinessAccountId("test_business_account_id");
        properties.setApiVersion("v18.0");
        properties.setOtpTemplateName("foodie_otp_verify");
        properties.setOtpTemplateLanguage("en");

        sender = new WhatsAppCloudApiSender(properties, restClient);
    }

    @Test
    void sendOtp_whenDisabled_suppressesSend() {
        properties.setEnabled(false);

        assertThatNoException().isThrownBy(() ->
                sender.sendOtp("+919876543210", "123456", OtpUserType.CUSTOMER, OtpPurpose.REGISTRATION)
        );

        verify(restClient, never()).post();
    }

    @Test
    void sendOtp_missingPhoneNumberId_throwsBadRequestException() {
        properties.setPhoneNumberId("");

        assertThatThrownBy(() ->
                sender.sendOtp("+919876543210", "123456", OtpUserType.CUSTOMER, OtpPurpose.REGISTRATION)
        ).isInstanceOf(BadRequestException.class);
    }

    @Test
    void sendOtp_validRequest_postsToGraphApi() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        assertThatNoException().isThrownBy(() ->
                sender.sendOtp("+919876543210", "123456", OtpUserType.RESTAURANT, OtpPurpose.REGISTRATION)
        );

        verify(restClient).post();
        verify(requestBodyUriSpec).uri("https://graph.facebook.com/v18.0/test_phone_number_id/messages");
        verify(requestBodySpec).header("Authorization", "Bearer test_access_token");
    }
}
