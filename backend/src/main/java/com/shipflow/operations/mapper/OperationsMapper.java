package com.shipflow.operations.mapper;

import com.shipflow.operations.domain.OperationsSummary;
import com.shipflow.operations.domain.OperationsTodos;
import com.shipflow.operations.domain.WorkbenchCounts;
import com.shipflow.operations.domain.WorkbenchRecentOrder;
import com.shipflow.operations.domain.WorkbenchRisk;
import com.shipflow.operations.domain.MetricDrilldownItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OperationsMapper {
    OperationsSummary findSummary(@Param("tenantId") Long tenantId);
    OperationsTodos findTodos(@Param("tenantId") Long tenantId);

    List<Long> findVisibleStoreIds(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                                   @Param("storeId") Long storeId);

    WorkbenchCounts findWorkbenchCounts(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                                        @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                        @Param("storeId") Long storeId);

    List<WorkbenchRecentOrder> findRecentOrders(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                                                @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                                @Param("storeId") Long storeId, @Param("sortBy") String sortBy,
                                                @Param("sortDirection") String sortDirection,
                                                @Param("offset") int offset, @Param("limit") int limit);

    List<WorkbenchRisk> findRisks(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                                  @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                  @Param("storeId") Long storeId, @Param("asOf") LocalDateTime asOf,
                                  @Param("thresholdHours") int thresholdHours,
                                  @Param("limit") int limit);

    long countMetricDrilldown(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                              @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                              @Param("storeId") Long storeId, @Param("metricKey") String metricKey);

    List<MetricDrilldownItem> findMetricDrilldown(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                                                   @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                                   @Param("storeId") Long storeId, @Param("metricKey") String metricKey,
                                                   @Param("offset") int offset, @Param("limit") int limit);
}
