package com.shipflow.logistics.mapper;

import com.shipflow.logistics.domain.model.LogisticsChannel;
import com.shipflow.logistics.domain.model.LogisticsProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface LogisticsMasterMapper {
    LogisticsProvider findProvider(@Param("providerId") Long providerId);
    LogisticsProvider findProviderByCode(@Param("providerCode") String providerCode);
    List<LogisticsProvider> pageProviders(@Param("status") String status, @Param("offset") int offset, @Param("pageSize") int pageSize);
    long countProviders(@Param("status") String status);
    int insertProvider(LogisticsProvider provider);
    int updateProvider(@Param("providerId") Long providerId, @Param("providerName") String providerName, @Param("status") String status, @Param("version") long version);
    LogisticsChannelRow findChannel(@Param("channelId") Long channelId);
    LogisticsChannelRow findChannelByCode(@Param("providerId") Long providerId, @Param("channelCode") String channelCode);
    List<LogisticsChannelRow> pageChannels(@Param("providerId") Long providerId, @Param("status") String status, @Param("offset") int offset, @Param("pageSize") int pageSize);
    long countChannels(@Param("providerId") Long providerId, @Param("status") String status);
    int insertChannel(LogisticsChannel channel);
    int updateChannel(@Param("channelId") Long channelId, @Param("channelName") String channelName, @Param("transportMode") String transportMode, @Param("serviceArea") String serviceArea, @Param("status") String status, @Param("version") long version);
    List<String> findServiceCountries(@Param("channelId") Long channelId);
    int deleteServiceCountries(@Param("channelId") Long channelId);
    int insertServiceCountry(@Param("channelId") Long channelId, @Param("countryCode") String countryCode);
    boolean hasPublishedPriceRule(@Param("channelId") Long channelId);
    int insertPublishedPriceRule(@Param("channelId") Long channelId, @Param("versionNo") int versionNo, @Param("ruleName") String ruleName, @Param("currency") String currency, @Param("volumeDivisor") BigDecimal volumeDivisor, @Param("roundingMode") String roundingMode, @Param("roundingIncrement") BigDecimal roundingIncrement, @Param("effectiveFrom") LocalDateTime effectiveFrom);
    Long findPriceRuleId(@Param("channelId") Long channelId, @Param("versionNo") int versionNo);
    PriceRuleRow findPublishedPriceRule(@Param("priceRuleId") Long priceRuleId);
    List<com.shipflow.logistics.domain.model.PriceRuleTier> findPriceRuleTiers(@Param("priceRuleId") Long priceRuleId);
    int insertPriceRuleTier(@Param("priceRuleId") Long priceRuleId, @Param("tier") com.shipflow.logistics.domain.model.PriceRuleTier tier);
}
