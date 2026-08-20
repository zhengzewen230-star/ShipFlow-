package com.shipflow.warehouse.domain;

/**
 * MyBatis bean projection for the warehouse overview aggregates.
 *
 * <p>A bean result map is used instead of constructor auto-detection because
 * MySQL aggregate expressions may be exposed as different JDBC numeric types.
 * The explicit Long properties keep the SQL-to-domain contract stable.</p>
 */
public final class WarehouseOverviewCounts {
    private Long pendingInbound;
    private Long pendingMeasurement;
    private Long pendingLabel;
    private Long pendingHandover;
    private Long pendingOutbound;
    private Long inTransit;
    private Long trackingExceptions;
    private Long todayInbound;
    private Long todayOutbound;

    public WarehouseOverviewCounts() {
    }

    public Long getPendingInbound() {
        return pendingInbound;
    }

    public void setPendingInbound(Long pendingInbound) {
        this.pendingInbound = pendingInbound;
    }

    public Long getPendingMeasurement() {
        return pendingMeasurement;
    }

    public void setPendingMeasurement(Long pendingMeasurement) {
        this.pendingMeasurement = pendingMeasurement;
    }

    public Long getPendingOutbound() {
        return pendingOutbound;
    }

    public void setPendingOutbound(Long pendingOutbound) {
        this.pendingOutbound = pendingOutbound;
    }

    public Long getPendingLabel() { return pendingLabel; }

    public void setPendingLabel(Long pendingLabel) { this.pendingLabel = pendingLabel; }

    public Long getPendingHandover() { return pendingHandover; }

    public void setPendingHandover(Long pendingHandover) { this.pendingHandover = pendingHandover; }

    public Long getInTransit() {
        return inTransit;
    }

    public void setInTransit(Long inTransit) {
        this.inTransit = inTransit;
    }

    public Long getTrackingExceptions() {
        return trackingExceptions;
    }

    public void setTrackingExceptions(Long trackingExceptions) {
        this.trackingExceptions = trackingExceptions;
    }

    public Long getTodayInbound() {
        return todayInbound;
    }

    public void setTodayInbound(Long todayInbound) {
        this.todayInbound = todayInbound;
    }

    public Long getTodayOutbound() {
        return todayOutbound;
    }

    public void setTodayOutbound(Long todayOutbound) {
        this.todayOutbound = todayOutbound;
    }
}
