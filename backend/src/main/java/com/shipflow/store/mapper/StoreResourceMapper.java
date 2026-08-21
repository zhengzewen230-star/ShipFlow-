package com.shipflow.store.mapper;

import com.shipflow.store.domain.model.StoreAddress;
import com.shipflow.store.domain.model.StoreChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StoreResourceMapper {
    StoreAddress findDefaultAddress(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId);
    StoreAddress findAddressByCode(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId, @Param("addressCode") String addressCode);
    int clearOtherDefaultAddresses(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId, @Param("addressId") Long addressId);
    int insertAddress(StoreAddress address);
    int updateAddress(StoreAddress address);

    List<StoreChannel> findChannels(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId);
    StoreChannel findChannel(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId, @Param("channelId") Long channelId);
    StoreChannel findDefaultChannel(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId);
    StoreChannel findPublicActiveChannel(@Param("channelId") Long channelId);
    int clearOtherDefaultChannels(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId, @Param("channelId") Long channelId);
    int insertChannel(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId, @Param("channelId") Long channelId, @Param("operatorId") Long operatorId);
    int updateChannelDefault(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId, @Param("channelId") Long channelId, @Param("version") Long version, @Param("operatorId") Long operatorId);
    long configurationVersion(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId);
}
