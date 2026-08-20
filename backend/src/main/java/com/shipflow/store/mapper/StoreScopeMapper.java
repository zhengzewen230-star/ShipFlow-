package com.shipflow.store.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StoreScopeMapper {
    boolean canAccessStore(@Param("tenantId") Long tenantId,
                           @Param("userId") Long userId,
                           @Param("storeId") Long storeId);

    boolean canRequestPriceConfirmation(@Param("tenantId") Long tenantId,
                                        @Param("userId") Long userId,
                                        @Param("storeId") Long storeId);

    boolean canConfirmPrice(@Param("tenantId") Long tenantId,
                            @Param("userId") Long userId,
                            @Param("storeId") Long storeId);

    boolean canManageStoreConfiguration(@Param("tenantId") Long tenantId,
                                        @Param("userId") Long userId,
                                        @Param("storeId") Long storeId);

    boolean canManageStoreStatus(@Param("tenantId") Long tenantId,
                                 @Param("userId") Long userId,
                                 @Param("storeId") Long storeId);
}
