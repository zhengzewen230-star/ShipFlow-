package com.shipflow.order.application;

import com.shipflow.order.domain.model.ShipmentOrderListQuery;
import com.shipflow.order.mapper.ShipmentOrderListRow;
import com.shipflow.order.mapper.ShipmentOrderMapper;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class ShipmentOrderExportApplicationService {
    public static final int MAX_EXPORT_ROWS = 1000;
    private final ShipmentOrderMapper mapper;
    public ShipmentOrderExportApplicationService(ShipmentOrderMapper mapper) { this.mapper = mapper; }
    public byte[] export(Long tenant, Long user, String orderNo, Long storeId, String status, String destination,
                         Long channelId, String trackingNo, LocalDateTime createdFrom, LocalDateTime createdTo,
                         String sortBy, String sortDirection, List<Long> ids) {
        if (ids != null && ids.size() > MAX_EXPORT_ROWS) throw new ShipmentOrderException("COMMON-1001", 400);
        ShipmentOrderListQuery query = new ShipmentOrderListQuery(orderNo, storeId, status, destination, channelId,
                trackingNo, createdFrom, createdTo, 1, MAX_EXPORT_ROWS, sortBy, sortDirection);
        List<ShipmentOrderListRow> rows = mapper.findExportRowsForCaller(tenant, user, query, ids);
        StringBuilder csv = new StringBuilder("\uFEFF订单号,店铺,状态,目的地,物流渠道,顺丰单号,费用,币种,计费重量,创建时间\r\n");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.ROOT);
        for (ShipmentOrderListRow row : rows) {
            csv.append(value(row.orderNo())).append(',').append(value(row.storeId())).append(',').append(value(row.status())).append(',')
                    .append(value(row.destinationCountry())).append(',').append(value(row.channelId())).append(',').append(value(row.trackingNo())).append(',')
                    .append(value(row.currentFee() == null ? row.estimatedFee() : row.currentFee())).append(',').append(value(row.currency())).append(',')
                    .append(value(row.chargeableWeight())).append(',').append(value(row.createdAt() == null ? null : formatter.format(row.createdAt()))).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
    private String value(Object value) { String text = value == null ? "" : String.valueOf(value); return '"' + text.replace("\"", "\"\"") + '"'; }
}
