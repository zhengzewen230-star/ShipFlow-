package com.shipflow.tracking.scheduler;

import com.shipflow.sf.config.SfProperties;
import com.shipflow.tracking.mapper.ShipmentTrackingMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

@Component
public class SfTrackingSimulationScheduler {
    private static final String PICKED_UP = "SF_PICKED_UP";
    private static final String CUSTOMS_CLEARED = "CUSTOMS_EXPORT_CLEARED";
    private static final String AIR_IN_TRANSIT = "AIR_IN_TRANSIT";
    private static final String DELIVERED = "DELIVERED";
    private static final String TRANSPORT_EXCEPTION = "TRANSPORT_EXCEPTION";
    private static final double TRANSPORT_EXCEPTION_PROBABILITY = 0.30d;
    private static final String SIMULATED_EXCEPTION_DESCRIPTION =
            "模拟物流轨迹检测到运输异常：物流商运输状态异常，等待人工处理。";

    private final ShipmentTrackingMapper mapper;
    private final SfProperties properties;
    private final Clock clock;
    private final DoubleSupplier random;

    @Autowired
    public SfTrackingSimulationScheduler(ShipmentTrackingMapper mapper, SfProperties properties, Clock clock) {
        this(mapper, properties, clock, () -> ThreadLocalRandom.current().nextDouble());
    }

    public SfTrackingSimulationScheduler(ShipmentTrackingMapper mapper, SfProperties properties, Clock clock,
                                         DoubleSupplier random) {
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
        this.random = random;
    }

    @Scheduled(fixedRate = 25000)
    @Transactional
    public void progressSandboxOrders() {
        if (!properties.isSandboxEnabled()) {
            return;
        }
        for (ShipmentTrackingMapper.SandboxOrder order : mapper.findSandboxOrders()) {
            progress(order);
        }
    }

    void progress(ShipmentTrackingMapper.SandboxOrder order) {
        String latest = mapper.findLatestSimulationStatus(order.tenantId(), order.orderId());
        Step next = next(order.currentStatus(), latest);
        if (next == null) {
            return;
        }
        LocalDateTime occurredAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (random.getAsDouble() < TRANSPORT_EXCEPTION_PROBABILITY) {
            String exceptionNo = "SIMEX-" + order.orderId();
            if (mapper.insertSimulationTransportException(order.tenantId(), order.orderId(), exceptionNo,
                    SIMULATED_EXCEPTION_DESCRIPTION, occurredAt) == 1) {
                mapper.insertSimulationExceptionAudit(order.tenantId(), exceptionNo,
                        SIMULATED_EXCEPTION_DESCRIPTION, occurredAt);
                mapper.insertSimulationEvent(order.tenantId(), order.orderId(), order.orderNo(), order.waybillNo(),
                        TRANSPORT_EXCEPTION, "运输异常（模拟）", SIMULATED_EXCEPTION_DESCRIPTION,
                        next.location(order.destinationAddress()), occurredAt);
            }
            return;
        }
        if (!next.targetStatus().equals(order.currentStatus())
                && mapper.transitionOrder(order.tenantId(), order.orderId(), order.currentStatus(),
                next.targetStatus(), order.version()) != 1) {
            return;
        }
        mapper.insertSimulationEvent(order.tenantId(), order.orderId(), order.orderNo(), order.waybillNo(),
                next.statusCode(), next.title(), next.description(), next.location(order.destinationAddress()), occurredAt);
    }

    private Step next(String currentStatus, String latest) {
        if (DELIVERED.equals(latest) || DELIVERED.equals(currentStatus)) {
            return null;
        }
        if (latest == null || "OUTBOUND".equals(latest)) {
            return Step.PICKED_UP;
        }
        return switch (latest) {
            case PICKED_UP -> Step.CUSTOMS_CLEARED;
            case CUSTOMS_CLEARED -> Step.AIR_IN_TRANSIT;
            case AIR_IN_TRANSIT -> Step.DELIVERED;
            case TRANSPORT_EXCEPTION -> null;
            default -> "OUTBOUND".equals(currentStatus) ? Step.PICKED_UP : null;
        };
    }

    private enum Step {
        PICKED_UP(SfTrackingSimulationScheduler.PICKED_UP, "顺丰速运 已收取快件", "顺丰速运已收取快件", "深圳集散中心", "IN_TRANSIT"),
        CUSTOMS_CLEARED(SfTrackingSimulationScheduler.CUSTOMS_CLEARED, "【海关放行】出口商业报关完成", "出口商业报关完成", "深圳宝安国际机场", "IN_TRANSIT"),
        AIR_IN_TRANSIT(SfTrackingSimulationScheduler.AIR_IN_TRANSIT, "国际干线航班抵达目的国", "国际干线航班已抵达目的国", "美国洛杉矶国际枢纽", "IN_TRANSIT"),
        DELIVERED(SfTrackingSimulationScheduler.DELIVERED, "快件已妥投签收", "快件已妥投签收", null, "DELIVERED");

        private final String statusCode;
        private final String title;
        private final String description;
        private final String location;
        private final String targetStatus;

        Step(String statusCode, String title, String description, String location, String targetStatus) {
            this.statusCode = statusCode;
            this.title = title;
            this.description = description;
            this.location = location;
            this.targetStatus = targetStatus;
        }

        private String location(String destinationAddress) {
            return location == null ? destinationAddress : location;
        }

        private String statusCode() { return statusCode; }
        private String title() { return title; }
        private String description() { return description; }
        private String targetStatus() { return targetStatus; }
    }
}
