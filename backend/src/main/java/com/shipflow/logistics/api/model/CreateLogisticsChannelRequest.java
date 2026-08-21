package com.shipflow.logistics.api.model;
import com.shipflow.logistics.domain.model.LogisticsChannel.TransportMode; import jakarta.validation.constraints.*;
public record CreateLogisticsChannelRequest(@NotNull Long providerId, @NotBlank @Size(max=64) String channelCode, @NotBlank @Size(max=128) String channelName, @NotNull TransportMode transportMode, @NotBlank @Size(max=255) String serviceArea) { }
