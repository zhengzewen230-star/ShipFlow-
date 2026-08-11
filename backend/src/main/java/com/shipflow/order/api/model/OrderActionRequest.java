package com.shipflow.order.api.model;
import jakarta.validation.constraints.NotNull;
public record OrderActionRequest(@NotNull Long version, String reason) { }
