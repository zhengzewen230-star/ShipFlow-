package com.shipflow.warehouse.application;

import com.shipflow.warehouse.api.model.WarehouseWorkItemResponse;
import com.shipflow.warehouse.api.model.WarehouseWorkPageResponse;
import com.shipflow.warehouse.domain.WarehouseWorkItem;
import com.shipflow.warehouse.mapper.WarehouseWorkMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneOffset;

@Service
public class WarehouseWorkApplicationService {
    private final WarehouseWorkMapper mapper;

    public WarehouseWorkApplicationService(WarehouseWorkMapper mapper) {
        this.mapper = mapper;
    }

    @Autowired
    public WarehouseWorkApplicationService(ObjectProvider<WarehouseWorkMapper> mapperProvider) {
        this.mapper = mapperProvider.getIfAvailable();
    }

    @Transactional(readOnly = true)
    public WarehouseWorkPageResponse list(Long tenantId, String status, String orderNo, int page, int pageSize) {
        validateTenant(tenantId);
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new WarehouseException("COMMON-1001", 400);
        }
        requireMapper();
        String normalizedStatus = normalize(status);
        String normalizedOrderNo = normalize(orderNo);
        long total = mapper.count(tenantId, normalizedStatus, normalizedOrderNo);
        return new WarehouseWorkPageResponse(page, pageSize, (total + pageSize - 1) / pageSize, total,
                mapper.findPage(tenantId, normalizedStatus, normalizedOrderNo, (page - 1) * pageSize, pageSize)
                        .stream().map(this::response).toList());
    }

    @Transactional(readOnly = true)
    public WarehouseWorkItemResponse get(Long tenantId, Long orderId) {
        validateTenant(tenantId);
        if (orderId == null || orderId < 1) throw new WarehouseException("COMMON-1001", 400);
        requireMapper();
        WarehouseWorkItem item = mapper.findById(tenantId, orderId);
        if (item == null) throw new WarehouseException("COMMON-1006", 404);
        return response(item);
    }

    private WarehouseWorkItemResponse response(WarehouseWorkItem item) {
        java.math.BigDecimal difference = item.feeDifference() == null ? java.math.BigDecimal.ZERO : item.feeDifference();
        return new WarehouseWorkItemResponse(item.id(), item.businessOrderNo(), item.sfTrackingNo(), item.tenantId(),
                item.tenantName(), item.destinationCountry(), item.declaredWeight(), item.declaredLength(),
                item.declaredWidth(), item.declaredHeight(), item.declaredVolumeWeight(), item.actualWeight(),
                item.actualLength(), item.actualWidth(), item.actualHeight(), item.actualVolumeWeight(),
                item.chargeableWeight(), item.estimatedFee(), item.currentFee(), item.currency(), difference,
                difference.signum() != 0, item.warehouseStatus(), item.logisticsStatus(), item.version(),
                utc(item.outboundAt()), item.outboundBy(), utc(item.createdAt()));
    }

    private java.time.OffsetDateTime utc(java.time.LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateTenant(Long tenantId) {
        if (tenantId == null || tenantId < 1) throw new WarehouseException("COMMON-1004", 403);
    }

    private void requireMapper() {
        if (mapper == null) throw new WarehouseException("COMMON-1007", 500);
    }
}
