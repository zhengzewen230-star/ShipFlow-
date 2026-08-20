package com.shipflow.warehouse.mapper;

import com.shipflow.warehouse.domain.WarehouseWorkItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WarehouseWorkMapper {
    long count(@Param("tenantId") Long tenantId, @Param("status") String status,
               @Param("orderNo") String orderNo);

    List<WarehouseWorkItem> findPage(@Param("tenantId") Long tenantId, @Param("status") String status,
                                     @Param("orderNo") String orderNo, @Param("offset") int offset,
                                     @Param("pageSize") int pageSize);

    WarehouseWorkItem findById(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
}
