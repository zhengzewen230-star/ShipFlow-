package com.shipflow.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipflow.order.api.model.CreateShipmentOrderRequest;
import com.shipflow.order.application.ShipmentOrderApplicationService;
import com.shipflow.order.application.ShipmentOrderException;
import com.shipflow.order.domain.model.ShipmentOrder;
import com.shipflow.order.mapper.ShipmentOrderIdempotencyMapper;
import com.shipflow.order.mapper.ShipmentOrderMapper;
import com.shipflow.quote.domain.model.Quote;
import com.shipflow.quote.mapper.QuoteMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal; import java.time.*; import java.util.List;
import static org.assertj.core.api.Assertions.*; import static org.mockito.ArgumentMatchers.*; import static org.mockito.Mockito.*;

class ShipmentOrderApplicationServiceTest {
 private final ShipmentOrderMapper orders=mock(ShipmentOrderMapper.class); private final ShipmentOrderIdempotencyMapper idem=mock(ShipmentOrderIdempotencyMapper.class); private final QuoteMapper quotes=mock(QuoteMapper.class);
 private final Clock clock=Clock.fixed(Instant.parse("2026-08-11T08:00:00Z"), ZoneOffset.UTC);
 private final ShipmentOrderApplicationService service=new ShipmentOrderApplicationService(orders,idem,quotes,new ObjectMapper(),clock);
 @Test void createsAllSnapshotsAndCompletesIdempotency(){ when(idem.find(7L,"createShipmentOrderFromQuote","key")).thenReturn(null); when(quotes.findById(7L,99L)).thenReturn(quote()); when(orders.findByQuoteId(7L,99L)).thenReturn(null); when(orders.findByIdempotencyKey(7L,"key")).thenReturn(order()); when(orders.findPackageId(7L,88L)).thenReturn(77L);
   var response=service.create(7L,2L,99L,request(),"key","r1"); assertThat(response.id()).isEqualTo(88L); verify(orders).insertSnapshot(eq(7L),eq(88L),eq(quote()),eq(new BigDecimal("5000")),eq("CEILING"),eq(new BigDecimal("0.5"))); verify(orders,times(2)).insertAddress(eq(7L),eq(88L),anyString(),any()); verify(orders).insertPackage(eq(7L),eq(88L),anyString(),any()); verify(orders).insertItem(eq(7L),eq(77L),eq(1),any()); verify(orders).insertAudit(7L,2L,88L,"r1",LocalDateTime.of(2026,8,11,8,0)); verify(idem).complete(7L,"createShipmentOrderFromQuote","key",88L); }
 @Test void rejectsExpiredOrPreviouslyUsedQuote(){ when(idem.find(7L,"createShipmentOrderFromQuote","key")).thenReturn(null); when(quotes.findById(7L,99L)).thenReturn(new Quote(99L,7L,"Q",3L,4L,5L,2,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,"USD","{}",LocalDateTime.MIN,LocalDateTime.of(2026,8,11,7,59),"VALID",0,null,null)); assertThatThrownBy(()->service.create(7L,2L,99L,request(),"key",null)).isInstanceOf(ShipmentOrderException.class).satisfies(e->assertThat(((ShipmentOrderException)e).code()).isEqualTo("QUOTE-1003")); }
 @Test void rejectsSameKeyWithDifferentRequest(){ when(idem.find(7L,"createShipmentOrderFromQuote","key")).thenReturn(new ShipmentOrderIdempotencyMapper.Record("different",88L)); assertThatThrownBy(()->service.create(7L,2L,99L,request(),"key",null)).isInstanceOf(ShipmentOrderException.class).satisfies(e->assertThat(((ShipmentOrderException)e).code()).isEqualTo("COMMON-1009")); }
 private Quote quote(){return new Quote(99L,7L,"Q",3L,4L,5L,2,new BigDecimal("1.2"),new BigDecimal("20"),new BigDecimal("20"),new BigDecimal("20"),new BigDecimal("1.6"),new BigDecimal("2"),new BigDecimal("12"),"USD","{\"destinationCountry\":\"US\",\"volumeDivisor\":5000,\"roundingMode\":\"CEILING\",\"roundingIncrement\":0.5}",LocalDateTime.of(2026,8,11,7,0),LocalDateTime.of(2026,8,11,9,0),"VALID",0,null,null);}
 private ShipmentOrder order(){return new ShipmentOrder(88L,7L,"SO1","key",3L,99L,4L,"DRAFT","CN","US",new BigDecimal("1.2"),new BigDecimal("20"),new BigDecimal("20"),new BigDecimal("20"),new BigDecimal("1.6"),new BigDecimal("2"),new BigDecimal("12"),"USD",0L,LocalDateTime.of(2026,8,11,8,0));}
 private CreateShipmentOrderRequest request(){var a=new CreateShipmentOrderRequest.Address("A","1",null,null,"CN",null,"SZ",null,"line",null,"1");var b=new CreateShipmentOrderRequest.Address("B","2",null,null,"US",null,"LA",null,"line",null,"2"); return new CreateShipmentOrderRequest(a,b,List.of(new CreateShipmentOrderRequest.Item("sku","item",BigDecimal.ONE,BigDecimal.ONE,"USD",null,null)));}
}
