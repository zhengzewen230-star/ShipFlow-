package com.shipflow.quote.mapper;

import com.shipflow.logistics.domain.model.PriceRuleTier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface QuotePricingMapper {
    boolean isActiveChannel(@Param("channelId") Long channelId);
    boolean servesCountry(@Param("channelId") Long channelId, @Param("countryCode") String countryCode);
    QuotePricingRuleRow findCurrentRule(@Param("channelId") Long channelId, @Param("now") LocalDateTime now);
    List<PriceRuleTier> findTiers(@Param("priceRuleId") Long priceRuleId);
}
