package com.shipflow.logistics;

import com.shipflow.logistics.api.model.*;
import com.shipflow.logistics.application.*;
import com.shipflow.logistics.domain.model.*;
import com.shipflow.logistics.mapper.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*; import static org.mockito.Mockito.*;

class LogisticsMasterApplicationServiceTest {
    private final LogisticsMasterMapper mapper=mock(LogisticsMasterMapper.class); private final LogisticsIdempotencyMapper idempotency=mock(LogisticsIdempotencyMapper.class); private final LogisticsAuditMapper audit=mock(LogisticsAuditMapper.class);
    private final LogisticsMasterApplicationService service=new LogisticsMasterApplicationService(mapper,idempotency,audit,Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"),ZoneOffset.UTC));
    @Test void tenantCannotBeInferredForPlatformMasterData() {
        when(mapper.findProvider(1L)).thenReturn(new LogisticsProvider(1L,"P","Provider","ACTIVE",0L,null,null));
        assertThat(service.provider(1L).providerCode()).isEqualTo("P"); verifyNoInteractions(idempotency,audit);
    }
    @Test void channelActivationRequiresServiceCountriesAndPublishedRule() {
        when(mapper.findChannel(2L)).thenReturn(new LogisticsChannelRow(2L,1L,"C","Channel", LogisticsChannel.TransportMode.AIR,"CN-US","DISABLED",0L,null,null));
        when(mapper.findServiceCountries(2L)).thenReturn(List.of()); when(mapper.findProvider(1L)).thenReturn(new LogisticsProvider(1L,"P","Provider","ACTIVE",0L,null,null));
        assertThatThrownBy(() -> service.updateChannel(2L,new UpdateLogisticsChannelRequest("C", LogisticsChannel.TransportMode.AIR,"CN-US","ACTIVE",0L),1L,null)).isInstanceOf(LogisticsException.class).extracting(e->((LogisticsException)e).code()).isEqualTo("LOGISTICS-1003");
        verifyNoInteractions(audit);
    }
    @Test void providerCreateReplaysCompletedIdempotencyRequestWithoutSecondInsert() {
        when(idempotency.find("createLogisticsProvider","key")).thenReturn(new LogisticsIdempotencyMapper.Record("159b52d2d45fae9e51aacc6b1423ae2c81dda7f0ad171717fb09eff8d607751b",1L));
        when(mapper.findProvider(1L)).thenReturn(new LogisticsProvider(1L,"P1","Provider","ACTIVE",0L,null,null));
        assertThat(service.createProvider(new CreateLogisticsProviderRequest("P1","Provider"),"key",1L,null).id()).isEqualTo(1L);
        verify(mapper,never()).insertProvider(any()); verifyNoInteractions(audit);
    }
    @Test void providerCreateRejectsSameKeyWithDifferentBody() {
        when(idempotency.find("createLogisticsProvider","key")).thenReturn(new LogisticsIdempotencyMapper.Record("different",1L));
        assertThatThrownBy(() -> service.createProvider(new CreateLogisticsProviderRequest("P1","Provider"),"key",1L,null)).isInstanceOf(LogisticsException.class).extracting(e->((LogisticsException)e).code()).isEqualTo("COMMON-1009");
        verify(mapper,never()).insertProvider(any());
    }
    @Test void tenantCatalogueExcludesDisabledProviderOrChannel() {
        when(mapper.findAvailableChannel(2L)).thenReturn(null);
        assertThatThrownBy(() -> service.availableChannel(2L)).isInstanceOf(LogisticsException.class).extracting(e -> ((LogisticsException) e).code()).isEqualTo("COMMON-1006");
        verify(mapper).findAvailableChannel(2L);
    }
    @Test void publicCatalogueAppliesWhitelistedFiltersAndNeverLoadsPriceTiers() {
        when(mapper.countPublicChannels("SF", "Express", "US", "ACTIVE")).thenReturn(1L);
        when(mapper.pagePublicChannels("SF", "Express", "US", "ACTIVE", "channelName", "ASC", 0, 20))
                .thenReturn(List.of(new PublicLogisticsChannelRow(2L, "Provider", "SF-1", "Express", LogisticsChannel.TransportMode.AIR, "ACTIVE", 3, LocalDateTime.of(2026, 8, 1, 0, 0), null, java.math.BigDecimal.valueOf(5000))));
        when(mapper.findServiceCountries(2L)).thenReturn(List.of("US"));
        PublicLogisticsChannel channel = service.publicChannels(" SF ", " Express ", "us", "ACTIVE", 1, 20, "channelName", "ASC").items().getFirst();
        assertThat(channel.providerName()).isEqualTo("Provider");
        assertThat(channel.priceRuleVersion()).isEqualTo(3);
        assertThat(channel.unavailableFields()).contains("priceRuleTiers", "internalCost", "supplierConfiguration");
        verify(mapper, never()).findPriceRuleTiers(anyLong());
    }
    @Test void publicCatalogueRejectsUnknownSortField() {
        assertThatThrownBy(() -> service.publicChannels(null, null, null, null, 1, 20, "internalCost", "ASC"))
                .isInstanceOf(LogisticsException.class)
                .extracting(error -> ((LogisticsException) error).code()).isEqualTo("COMMON-1001");
    }
    @Test void priceRuleMustBelongToRequestedChannel() {
        when(mapper.findChannel(2L)).thenReturn(new LogisticsChannelRow(2L,1L,"C","Channel", LogisticsChannel.TransportMode.AIR,"CN-US","DISABLED",0L,null,null));
        when(mapper.findServiceCountries(2L)).thenReturn(List.of("US"));
        when(mapper.findPublishedPriceRule(9L)).thenReturn(new PriceRuleRow(9L,3L,1,"Rule","USD",java.math.BigDecimal.valueOf(5000), PublishedPriceRule.RoundingMode.CEILING,java.math.BigDecimal.ONE,LocalDateTime.now()));
        when(mapper.findPriceRuleTiers(9L)).thenReturn(List.of());
        assertThatThrownBy(() -> service.publishedPriceRule(2L,9L)).isInstanceOf(LogisticsException.class).extracting(e -> ((LogisticsException) e).code()).isEqualTo("COMMON-1006");
    }
}
