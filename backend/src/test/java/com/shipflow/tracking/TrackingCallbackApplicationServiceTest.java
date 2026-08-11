package com.shipflow.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipflow.tracking.application.TrackingCallbackApplicationService;
import com.shipflow.tracking.application.TrackingCallbackException;
import com.shipflow.tracking.config.TrackingCallbackProperties;
import com.shipflow.tracking.domain.ExistingTrackingEvent;
import com.shipflow.tracking.domain.TrackingCallbackOrder;
import com.shipflow.tracking.mapper.TrackingCallbackMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrackingCallbackApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");
    private TrackingCallbackMapper mapper;
    private TrackingCallbackApplicationService service;
    private byte[] key;

    @BeforeEach
    void setUp() {
        mapper = mock(TrackingCallbackMapper.class);
        key = new byte[32];
        Arrays.fill(key, (byte) 7);
        TrackingCallbackProperties properties = new TrackingCallbackProperties();
        TrackingCallbackProperties.Credential credential = new TrackingCallbackProperties.Credential();
        credential.setProviderCode("MOCK");
        credential.setSystemUserId(10L);
        credential.setHmacKey(Base64.getEncoder().encodeToString(key));
        properties.setCredentials(java.util.List.of(credential));
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new TrackingCallbackApplicationService(mapper, properties, objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void authenticatesPersistsAdvancesAndAuditsFirstEvent() throws Exception {
        byte[] body = body("IN_TRANSIT", "{\"node\":\"departed\"}");
        authorize();
        when(mapper.lockOrder(1L, "TRK-1")).thenReturn(new TrackingCallbackOrder(7L, 9L, "OUTBOUND"));
        when(mapper.advanceOrder(7L, 9L, "OUTBOUND", "IN_TRANSIT")).thenReturn(1);

        var result = service.receive("MOCK", timestamp(), signature(body), body, "request-1");

        assertThat(result.accepted()).isEqualTo(1);
        assertThat(result.duplicated()).isZero();
        assertThat(result.events().get(0).status()).isEqualTo("ACCEPTED");
        verify(mapper).insertEvent(eq(7L), eq(1L), eq(9L), eq("TRK-1"), eq("EVT-1"),
                eq("IN_TRANSIT"), isNull(), any(), any(), eq("{\"node\":\"departed\"}"),
                eq("PROCESSED"), isNull());
        verify(mapper).advanceOrder(7L, 9L, "OUTBOUND", "IN_TRANSIT");
        verify(mapper).insertAudit(eq(7L), eq(10L), eq(9L), eq("request-1"),
                contains("statusAdvanced"), any());
    }

    @Test
    void duplicateDoesNotInsertAdvanceOrAudit() throws Exception {
        byte[] body = body("IN_TRANSIT", "{\"node\":\"departed\"}");
        authorize();
        when(mapper.lockOrder(1L, "TRK-1")).thenReturn(new TrackingCallbackOrder(7L, 9L, "IN_TRANSIT"));
        when(mapper.findEvent(1L, "TRK-1", "EVT-1"))
                .thenReturn(new ExistingTrackingEvent(5L, "{\"node\":\"departed\"}", "PROCESSED"));

        var result = service.receive("MOCK", timestamp(), signature(body), body, null);

        assertThat(result.duplicated()).isEqualTo(1);
        assertThat(result.events().get(0).status()).isEqualTo("DUPLICATE");
        verify(mapper, never()).insertEvent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(mapper, never()).advanceOrder(any(), any(), any(), any());
        verify(mapper, never()).insertAudit(any(), any(), any(), any(), any(), any());
    }

    @Test
    void lateEventIsSavedAndAuditedWithoutStateRollback() throws Exception {
        byte[] body = body("IN_TRANSIT", "{\"late\":true}");
        authorize();
        when(mapper.lockOrder(1L, "TRK-1")).thenReturn(new TrackingCallbackOrder(7L, 9L, "DELIVERED"));

        service.receive("MOCK", timestamp(), signature(body), body, null);

        verify(mapper).insertEvent(eq(7L), eq(1L), eq(9L), any(), any(), any(), any(), any(), any(),
                any(), eq("PROCESSED"), isNull());
        verify(mapper, never()).advanceOrder(any(), any(), any(), any());
        verify(mapper).insertAudit(eq(7L), eq(10L), eq(9L), isNull(), contains("false"), any());
    }

    @Test
    void illegalMappingIsRetainedForRetryAndReturnsUnifiedError() throws Exception {
        byte[] body = body("UNKNOWN", "{\"code\":\"x\"}");
        authorize();
        when(mapper.lockOrder(1L, "TRK-1")).thenReturn(new TrackingCallbackOrder(7L, 9L, "OUTBOUND"));

        assertThatThrownBy(() -> service.receive("MOCK", timestamp(), signature(body), body, null))
                .isInstanceOf(TrackingCallbackException.class)
                .satisfies(error -> {
                    assertThat(((TrackingCallbackException) error).code()).isEqualTo("TRACK-1002");
                    assertThat(((TrackingCallbackException) error).status()).isEqualTo(422);
                });
        verify(mapper).insertEvent(eq(7L), eq(1L), eq(9L), any(), any(), eq("UNKNOWN"), any(), any(), any(),
                any(), eq("RETRY"), eq("UNSUPPORTED_EVENT_CODE"));
        verify(mapper, never()).advanceOrder(any(), any(), any(), any());
        verify(mapper, never()).insertAudit(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsValidSignatureWhenSystemAccountLacksPermission() throws Exception {
        byte[] body = body("IN_TRANSIT", "{}");
        when(mapper.authorizedProviderId("MOCK", 10L)).thenReturn(null);

        assertThatThrownBy(() -> service.receive("MOCK", timestamp(), signature(body), body, null))
                .isInstanceOf(TrackingCallbackException.class)
                .satisfies(error -> {
                    assertThat(((TrackingCallbackException) error).code()).isEqualTo("COMMON-1004");
                    assertThat(((TrackingCallbackException) error).status()).isEqualTo(403);
                });
        verify(mapper, never()).lockOrder(any(), any());
    }

    @Test
    void invalidSignatureDoesNotQueryIdentityOrBusinessData() {
        byte[] body = body("IN_TRANSIT", "{}");
        assertThatThrownBy(() -> service.receive("MOCK", timestamp(), "invalid", body, null))
                .isInstanceOf(TrackingCallbackException.class)
                .satisfies(error -> assertThat(((TrackingCallbackException) error).code()).isEqualTo("TRACK-1004"));
        verifyNoInteractions(mapper);
    }

    @Test
    void providerTrackingMismatchUsesExistingNotFoundError() throws Exception {
        byte[] body = body("IN_TRANSIT", "{}");
        authorize();
        when(mapper.lockOrder(1L, "TRK-1")).thenReturn(null);

        assertThatThrownBy(() -> service.receive("MOCK", timestamp(), signature(body), body, null))
                .isInstanceOf(TrackingCallbackException.class)
                .satisfies(error -> assertThat(((TrackingCallbackException) error).code()).isEqualTo("TRACK-1005"));
    }

    @Test
    void sameBusinessKeyWithDifferentPayloadDoesNotOverwriteFirstEvent() throws Exception {
        byte[] body = body("IN_TRANSIT", "{\"node\":\"new\"}");
        authorize();
        when(mapper.lockOrder(1L, "TRK-1")).thenReturn(new TrackingCallbackOrder(7L, 9L, "OUTBOUND"));
        when(mapper.findEvent(1L, "TRK-1", "EVT-1"))
                .thenReturn(new ExistingTrackingEvent(5L, "{\"node\":\"original\"}", "PROCESSED"));

        assertThatThrownBy(() -> service.receive("MOCK", timestamp(), signature(body), body, null))
                .isInstanceOf(TrackingCallbackException.class)
                .satisfies(error -> assertThat(((TrackingCallbackException) error).code()).isEqualTo("TRACK-1003"));
        verify(mapper, never()).insertEvent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private void authorize() {
        when(mapper.authorizedProviderId("MOCK", 10L)).thenReturn(1L);
    }

    private String timestamp() {
        return Long.toString(NOW.getEpochSecond());
    }

    private byte[] body(String code, String rawPayload) {
        return ("{\"events\":[{\"trackingNo\":\"TRK-1\",\"eventId\":\"EVT-1\"," +
                "\"eventCode\":\"" + code + "\",\"eventTime\":\"2026-08-10T23:00:00Z\"," +
                "\"rawPayload\":" + rawPayload + "}]}").getBytes(StandardCharsets.UTF_8);
    }

    private String signature(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        mac.update(timestamp().getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        return Base64.getEncoder().encodeToString(mac.doFinal(body));
    }
}
