package com.shipflow.quote.mapper;

import com.shipflow.quote.domain.model.Quote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuoteMapper {
    Quote findById(@Param("tenantId") Long tenantId, @Param("quoteId") Long quoteId);

    Quote findByQuoteNo(@Param("tenantId") Long tenantId, @Param("quoteNo") String quoteNo);

    List<Quote> findPage(@Param("tenantId") Long tenantId,
                         @Param("storeId") Long storeId,
                         @Param("channelId") Long channelId,
                         @Param("status") String status,
                         @Param("offset") int offset,
                         @Param("pageSize") int pageSize);

    long count(@Param("tenantId") Long tenantId,
               @Param("storeId") Long storeId,
               @Param("channelId") Long channelId,
               @Param("status") String status);

    boolean hasShipmentOrder(@Param("tenantId") Long tenantId, @Param("quoteId") Long quoteId);

    int insert(Quote quote);
}
