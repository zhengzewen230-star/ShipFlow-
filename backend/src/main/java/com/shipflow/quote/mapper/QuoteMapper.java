package com.shipflow.quote.mapper;

import com.shipflow.quote.domain.model.Quote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface QuoteMapper {
    Quote findById(@Param("tenantId") Long tenantId, @Param("quoteId") Long quoteId);
    Quote findByIdForUser(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("quoteId") Long quoteId);

    Quote findByQuoteNo(@Param("tenantId") Long tenantId, @Param("quoteNo") String quoteNo);

    List<Quote> findPageForUser(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                         @Param("quoteNo") String quoteNo,
                         @Param("storeId") Long storeId,
                         @Param("channelId") Long channelId,
                         @Param("destinationCountry") String destinationCountry,
                         @Param("status") String status,
                         @Param("createdFrom") LocalDateTime createdFrom,
                         @Param("createdTo") LocalDateTime createdTo,
                         @Param("validFrom") LocalDateTime validFrom,
                         @Param("validTo") LocalDateTime validTo,
                         @Param("now") LocalDateTime now,
                         @Param("sortField") String sortField,
                         @Param("sortDirection") String sortDirection,
                         @Param("offset") int offset,
                         @Param("pageSize") int pageSize);

    long countForUser(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
               @Param("quoteNo") String quoteNo,
               @Param("storeId") Long storeId,
               @Param("channelId") Long channelId,
               @Param("destinationCountry") String destinationCountry,
               @Param("status") String status,
               @Param("createdFrom") LocalDateTime createdFrom,
               @Param("createdTo") LocalDateTime createdTo,
               @Param("validFrom") LocalDateTime validFrom,
               @Param("validTo") LocalDateTime validTo,
               @Param("now") LocalDateTime now);

    boolean hasShipmentOrder(@Param("tenantId") Long tenantId, @Param("quoteId") Long quoteId);
    boolean isStoreActive(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId);

    int insert(Quote quote);
}
