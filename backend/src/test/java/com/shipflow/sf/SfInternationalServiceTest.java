package com.shipflow.sf;

import com.shipflow.sf.api.model.SfOperation;
import com.shipflow.sf.application.SfIntegrationException;
import com.shipflow.sf.application.SfBusinessException;
import com.shipflow.sf.application.SfInternationalService;
import com.shipflow.sf.client.SfApiClient;
import com.shipflow.sf.client.SfApiRequest;
import com.shipflow.sf.client.SfApiResponse;
import com.shipflow.sf.api.model.SfOperationResponse;
import com.shipflow.sf.config.SfProperties;
import com.shipflow.sf.mapper.SfProviderOrderMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doNothing;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

class SfInternationalServiceTest {
    @Test
    void refusesProviderCallUntilOfficialSandboxContractIsConfigured() {
        SfProperties properties = new SfProperties();
        SfApiClient client = mock(SfApiClient.class);
        SfProviderOrderMapper providerMapper = mock(SfProviderOrderMapper.class);
        SfInternationalService service = new SfInternationalService(properties, client,
                Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC), providerMapper);

        assertThatThrownBy(() -> service.execute(7L, 31L, SfOperation.CREATE_ORDER, "sf-test-request", "{}"))
                .isInstanceOf(SfIntegrationException.class)
                .satisfies(error -> {
                    SfIntegrationException failure = (SfIntegrationException) error;
                    org.assertj.core.api.Assertions.assertThat(failure.code()).isEqualTo("SF-1001");
                    org.assertj.core.api.Assertions.assertThat(failure.status()).isEqualTo(422);
                });
        verifyNoInteractions(client);
    }

    @Test
    void rejectsBlankRequestIdBeforeProviderCall() {
        SfProperties properties = new SfProperties();
        SfApiClient client = mock(SfApiClient.class);
        SfProviderOrderMapper providerMapper = mock(SfProviderOrderMapper.class);
        SfInternationalService service = new SfInternationalService(properties, client, Clock.systemUTC(), providerMapper);

        assertThatThrownBy(() -> service.execute(7L, 31L, SfOperation.QUERY_ORDER, " ", "{}"))
                .isInstanceOf(SfIntegrationException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((SfIntegrationException) error).code()).isEqualTo("COMMON-1001"));
        verifyNoInteractions(client);
    }

    @Test
    void buildsCreatePayloadFromTenantOrderData() {
        SfProperties properties = readyProperties();
        SfApiClient client = mock(SfApiClient.class);
        SfProviderOrderMapper mapper = mock(SfProviderOrderMapper.class);
        SfProviderOrderMapper.Context order = new SfProviderOrderMapper.Context(9L, null, 31L,
                "READY_FOR_OUTBOUND", null, "SO-31", null, null);
        when(mapper.findOrder(7L, 31L)).thenReturn(order);
        when(mapper.hasMeasurement(7L, 31L)).thenReturn(true);
        when(mapper.findByRequestId(7L, "request-1")).thenReturn(null);
        when(mapper.findAddress(7L, 31L, "SENDER")).thenReturn(address("CN"));
        when(mapper.findAddress(7L, 31L, "RECEIVER")).thenReturn(address("US"));
        when(mapper.findItems(7L, 31L)).thenReturn(List.of(new SfProviderOrderMapper.ItemPayload("测试商品", "TEST ITEM", 1,
                new BigDecimal("10.00"), "USD", new BigDecimal("10.00"), "SKU-1", "CN")));
        when(client.execute(any())).thenReturn(new SfApiResponse(200, "request-1",
                "{\"code\":\"A1000\",\"msgData\":{\"sfWaybillNo\":\"SF-TEST\"}}", "COM_RECE_IUOP_CREATE_ORDER", "A1000"));

        SfInternationalService service = new SfInternationalService(properties, client,
                Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC), mapper);
        SfOperationResponse response = service.execute(7L, 31L, SfOperation.CREATE_ORDER, "request-1", "{}");

        org.mockito.ArgumentCaptor<SfApiRequest> request = org.mockito.ArgumentCaptor.forClass(SfApiRequest.class);
        verify(client).execute(request.capture());
        org.assertj.core.api.Assertions.assertThat(request.getValue().msgData())
                .contains("customerCode", "customerOrderNo", "senderInfo", "receiverInfo", "cargoDetails", "TEST ITEM");
        org.assertj.core.api.Assertions.assertThat(response.externalOrderNo()).startsWith("UAT-SF-ORDER-");
        org.assertj.core.api.Assertions.assertThat(response.trackingNo()).isEqualTo("SF-TEST");
        verify(mapper).markSuccess(eq(7L), eq(31L), eq("request-1"), eq("CREATED"),
                org.mockito.ArgumentMatchers.startsWith("UAT-SF-ORDER-"), eq("SF-TEST"), eq("UAT_CREATED"));
    }

    @Test
    void sandboxFillsMissingCriticalReferencesAndRetainsTestOrder() {
        SfProperties properties = readyProperties();
        SfApiClient client = mock(SfApiClient.class);
        SfProviderOrderMapper mapper = mock(SfProviderOrderMapper.class);
        when(mapper.findOrder(7L, 31L)).thenReturn(new SfProviderOrderMapper.Context(9L, null, 31L,
                "READY_FOR_OUTBOUND", null, "SO-31", null, null));
        when(mapper.hasMeasurement(7L, 31L)).thenReturn(true);
        when(mapper.findByRequestId(7L, "sandbox-request")).thenReturn(null);
        when(mapper.findAddress(7L, 31L, "SENDER")).thenReturn(address("CN"));
        when(mapper.findAddress(7L, 31L, "RECEIVER")).thenReturn(address("US"));
        when(mapper.findItems(7L, 31L)).thenReturn(List.of(new SfProviderOrderMapper.ItemPayload("TEST", "TEST", 1,
                new BigDecimal("10.00"), "USD", new BigDecimal("10.00"), "SKU-1", "CN")));
        when(client.execute(any())).thenReturn(new SfApiResponse(200, "sandbox-request",
                "{\"code\":\"A1000\",\"msgData\":{}}", "COM_RECE_IUOP_CREATE_ORDER", "A1000"));

        SfOperationResponse response = new SfInternationalService(properties, client,
                Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC), mapper)
                .execute(7L, 31L, SfOperation.CREATE_ORDER, "sandbox-request", "{}");

        org.assertj.core.api.Assertions.assertThat(response.externalOrderNo()).startsWith("UAT-SF-ORDER-");
        org.assertj.core.api.Assertions.assertThat(response.trackingNo()).startsWith("UAT-SF-WAYBILL-");
        org.mockito.ArgumentCaptor<java.time.LocalDateTime> retention = org.mockito.ArgumentCaptor.forClass(java.time.LocalDateTime.class);
        verify(mapper).insertProcessing(eq(7L), eq(31L), eq(9L), eq("sandbox-request"),
                eq(SfOperation.CREATE_ORDER.serviceCode()), eq(true), retention.capture());
        org.assertj.core.api.Assertions.assertThat(retention.getValue()).isEqualTo(java.time.LocalDateTime.of(2026, 9, 15, 0, 0));
    }

    @Test
    void refusesCreateBeforeMeasurement() {
        SfProperties properties = readyProperties();
        SfApiClient client = mock(SfApiClient.class);
        SfProviderOrderMapper mapper = mock(SfProviderOrderMapper.class);
        when(mapper.findOrder(7L, 31L)).thenReturn(new SfProviderOrderMapper.Context(9L, null, 31L,
                "READY_FOR_OUTBOUND", null, "SO-31", null, null));
        when(mapper.hasMeasurement(7L, 31L)).thenReturn(false);

        SfInternationalService service = new SfInternationalService(properties, client,
                Clock.systemUTC(), mapper);

        assertThatThrownBy(() -> service.execute(7L, 31L, SfOperation.CREATE_ORDER, "before-measurement", "{}"))
                .isInstanceOf(SfIntegrationException.class)
                .satisfies(error -> {
                    SfIntegrationException failure = (SfIntegrationException) error;
                    org.assertj.core.api.Assertions.assertThat(failure.code()).isEqualTo("WAREHOUSE-1002");
                    org.assertj.core.api.Assertions.assertThat(failure.status()).isEqualTo(409);
                });
        verifyNoInteractions(client);
    }

    @Test
    void strictModeMarksProviderOrderFailedWhenHttp200OmitsWaybill() {
        SfProperties properties = mock(SfProperties.class);
        when(properties.isSandboxEnabled()).thenReturn(false);
        when(properties.isStrictMode()).thenReturn(true);
        when(properties.getPartnerId()).thenReturn("partner");
        doNothing().when(properties).validateForCall();
        SfApiClient client = mock(SfApiClient.class);
        SfProviderOrderMapper mapper = mock(SfProviderOrderMapper.class);
        when(mapper.findOrder(7L, 31L)).thenReturn(new SfProviderOrderMapper.Context(9L, null, 31L,
                "READY_FOR_OUTBOUND", null, "SO-31", null, null));
        when(mapper.hasMeasurement(7L, 31L)).thenReturn(true);
        when(mapper.findByRequestId(7L, "strict-request")).thenReturn(null);
        when(mapper.findAddress(7L, 31L, "SENDER")).thenReturn(address("CN"));
        when(mapper.findAddress(7L, 31L, "RECEIVER")).thenReturn(address("US"));
        when(mapper.findItems(7L, 31L)).thenReturn(List.of(new SfProviderOrderMapper.ItemPayload("TEST", "TEST", 1,
                new BigDecimal("10.00"), "USD", new BigDecimal("10.00"), "SKU-1", "CN")));
        when(client.execute(any())).thenReturn(new SfApiResponse(200, "strict-request",
                "{\"code\":\"A1000\",\"msgData\":{}}", "COM_RECE_IUOP_CREATE_ORDER", "A1000"));

        SfInternationalService service = new SfInternationalService(properties, client,
                Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC), mapper);

        assertThatThrownBy(() -> service.execute(7L, 31L, SfOperation.CREATE_ORDER, "strict-request", "{}"))
                .isInstanceOf(SfBusinessException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((SfBusinessException) error).code()).isEqualTo("SF-1008"));
        verify(mapper).markFailure(eq(7L), eq(31L), eq("strict-request"), eq("SF-1008"), org.mockito.ArgumentMatchers.contains("sfWaybillNo"));
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).markSuccess(any(), any(), any(), any(), any(), any(), any());
    }

    private SfProperties readyProperties() {
        SfProperties properties = new SfProperties();
        properties.setApiBaseUrl(SfProperties.SANDBOX_BASE_URL);
        properties.setPartnerId("partner");
        properties.setCheckWord("secret");
        properties.setCustomerCode("customer");
        properties.setSandboxEnabled(true);
        return properties;
    }

    private SfProviderOrderMapper.AddressPayload address(String country) {
        return new SfProviderOrderMapper.AddressPayload("TEST", "000", "TEST", "test@example.invalid",
                country, "STATE", "CITY", "DISTRICT", "TEST ADDRESS", null, "00000");
    }
}
