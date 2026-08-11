package com.shipflow.order.api.model;
import java.util.List;
public record ShipmentOrderPageResponse(int page,int pageSize,long totalPages,long total,List<ShipmentOrderResponse> items) { }
