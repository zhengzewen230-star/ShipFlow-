package com.shipflow.warehouse.mapper;

import com.shipflow.order.domain.model.ShipmentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface WarehouseMapper {
    record IdempotencyRecord(String requestHash, Long resourceId) { }

    ShipmentOrder findOrder(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    Long packageId(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    int transition(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                   @Param("from") String from, @Param("to") String to, @Param("version") Long version);
    int insertMeasurement(@Param("tenantId") Long t, @Param("packageId") Long p,
                          @Param("weight") BigDecimal w, @Param("length") BigDecimal l,
                          @Param("width") BigDecimal wi, @Param("height") BigDecimal h,
                          @Param("volume") BigDecimal v, @Param("chargeable") BigDecimal c,
                          @Param("userId") Long u, @Param("at") LocalDateTime at);
    Long latestMeasurementId(@Param("tenantId") Long t, @Param("packageId") Long p);
    int insertAdjustment(@Param("tenantId") Long t, @Param("orderId") Long o,
                         @Param("measurementId") Long m, @Param("type") String type,
                         @Param("before") BigDecimal before, @Param("after") BigDecimal after,
                         @Param("diff") BigDecimal diff, @Param("currency") String currency);
    int updateFee(@Param("tenantId") Long t, @Param("orderId") Long o,
                  @Param("weight") BigDecimal w, @Param("fee") BigDecimal f,
                  @Param("status") String s, @Param("version") Long version);
    int outbound(@Param("tenantId") Long t, @Param("orderId") Long o, @Param("packageId") Long p,
                 @Param("tracking") String tracking, @Param("userId") Long u,
                 @Param("at") LocalDateTime at, @Param("remark") String r,
                 @Param("batchNo") String batchNo, @Param("manifestReference") String manifestReference);
    int audit(@Param("tenantId") Long t, @Param("userId") Long u, @Param("orderId") Long o,
              @Param("action") String a, @Param("requestId") String r, @Param("at") LocalDateTime at);

    IdempotencyRecord findIdempotency(@Param("tenantId") Long tenantId,
                                      @Param("operationId") String operationId,
                                      @Param("idempotencyKey") String idempotencyKey);
    int insertIdempotency(@Param("tenantId") Long tenantId, @Param("operationId") String operationId,
                          @Param("idempotencyKey") String idempotencyKey, @Param("requestHash") String requestHash,
                          @Param("requestPath") String requestPath, @Param("expiresAt") LocalDateTime expiresAt);
    int completeIdempotency(@Param("tenantId") Long tenantId, @Param("operationId") String operationId,
                            @Param("idempotencyKey") String idempotencyKey, @Param("resourceId") Long resourceId);

}
