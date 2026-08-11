package com.shipflow.quote.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface QuoteAuditMapper {
    int insert(@Param("tenantId") Long tenantId, @Param("operatorUserId") Long operatorUserId,
               @Param("quoteId") Long quoteId, @Param("requestId") String requestId,
               @Param("occurredAt") LocalDateTime occurredAt);
}
