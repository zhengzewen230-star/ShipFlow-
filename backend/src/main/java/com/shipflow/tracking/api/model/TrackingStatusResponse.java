package com.shipflow.tracking.api.model;public record TrackingStatusResponse(Long orderId,String currentStatus,TrackingEventResponse latestEvent){}
