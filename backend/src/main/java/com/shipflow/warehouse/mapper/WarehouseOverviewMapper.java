package com.shipflow.warehouse.mapper;

import com.shipflow.warehouse.domain.WarehouseOverview;
import com.shipflow.warehouse.domain.WarehouseOverviewCounts;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WarehouseOverviewMapper {
    WarehouseOverviewCounts findOverviewCounts(@Param("tenantId") Long tenantId);
    List<WarehouseOverview.RecentWarehouseOrder> findRecentOrders(@Param("tenantId") Long tenantId, @Param("limit") int limit);
    List<WarehouseOverview.RecentTrackingException> findRecentTrackingExceptions(@Param("tenantId") Long tenantId, @Param("limit") int limit);
}
